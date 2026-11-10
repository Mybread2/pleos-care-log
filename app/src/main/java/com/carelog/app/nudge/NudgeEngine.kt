package com.carelog.app.nudge

import android.util.Log
import com.carelog.app.AppSettings
import com.carelog.app.data.CareLogDao
import com.carelog.app.data.NudgeEventEntity
import com.carelog.app.vehicle.CarState
import com.carelog.app.vehicle.VehicleGateway
import com.carelog.app.voice.SpeechGateway
import com.carelog.app.voice.VoiceIntent
import com.carelog.app.voice.VoiceIntentParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.roundToInt

/** 차량 상태를 읽어 [NudgePolicy]로 판단하고, 고른 넛지의 대화를 진행한 뒤 이력을 남긴다. */
class NudgeEngine(
    private val dao: CareLogDao,
    private val vehicle: VehicleGateway,
    private val car: CarState,
    private val speech: SpeechGateway,
    private val settings: AppSettings,
) {
    private val mutex = Mutex()
    private var previousBelt: Boolean? = null

    /** 마지막으로 판단에 쓴 상태 (디버그 화면 표시용). */
    @Volatile
    var lastContext: NudgeContext? = null
        private set

    suspend fun tick(): NudgeId? = mutex.withLock {
        val care = vehicle.readCare()
        val ctx = NudgeContext(
            now = System.currentTimeMillis(),
            nudgeEnabled = settings.nudgeEnabled,
            parked = car.isParked(),
            isEv = care.isEv,
            outsideTempC = settings.outsideTempOverrideC ?: car.outsideTempC(),
            steeringHeatLevel = care.steeringHeatLevel,
            driverBeltBuckled = care.driverBeltBuckled,
            previousBeltBuckled = previousBelt,
            anyWindowOpen = care.anyWindowOpen,
            lastVoiceAt = speech.lastConversationEndedAt,
            hvac = care.hvac,
            convenienceGapMs = settings.convenienceGapMinutes.takeIf { it > 0 }?.times(60_000L),
        )
        lastContext = ctx
        previousBelt = care.driverBeltBuckled

        val events = dao.nudgeEventsSince(settings.tripStartedAt).mapNotNull { e ->
            runCatching { NudgeEvent(NudgeId.valueOf(e.nudge), e.at, e.outcome) }.getOrNull()
        }
        val nudge = NudgePolicy.decide(ctx, events) ?: return@withLock null

        val outcome = speech.conversation { run(nudge, ctx) }
        dao.insertNudgeEvent(NudgeEventEntity(nudge = nudge.name, at = ctx.now, outcome = outcome))
        Log.i(TAG, "$nudge → $outcome")
        nudge
    }

    private suspend fun SpeechGateway.run(nudge: NudgeId, ctx: NudgeContext): String {
        // 다른 대화를 기다리는 동안 출발했을 수 있다.
        if (car.isParked() != true) return NudgeOutcome.CANCELLED
        return when (nudge) {
            NudgeId.ICY_ROAD -> spoken(speak("기온이 낮아 노면이 얼 수 있어요."))
            NudgeId.WINDOW_OPEN -> spoken(speak("창문이 열려 있어요."))
            NudgeId.WINTER_HEAT -> if (settings.autoHeatConsent) autoHeat() else offerHeat(ctx.outsideTempC)
            NudgeId.HVAC_ADJUST -> offerHvac(ctx.hvac?.driverSetpointC)
        }
    }

    /** 스마트 자동 케어: 사전 동의한 범위(정차 중 핸들 열선 1단)만 묻지 않고 실행한 뒤 알린다 (PRD 6.3). */
    private suspend fun SpeechGateway.autoHeat(): String {
        if (vehicle.readCare().steeringHeatLevel != 0) return NudgeOutcome.CANCELLED
        val ok = vehicle.setSteeringHeat(HEAT_LEVEL)
        speak(if (ok) "미리 동의하신 대로 핸들 열선을 1단으로 켰어요." else "핸들 열선을 켜지 못했어요.")
        return if (ok) NudgeOutcome.AUTO_EXECUTED else NudgeOutcome.FAILED
    }

    /**
     * 운전석 설정 온도만 한 번 낮춘다 (PRD 기능 5 ⑤). 수락 뒤 다시 읽어
     * 직접 조작·냉방·제상 등으로 상태가 바뀌었으면 실행하지 않는다.
     */
    private suspend fun SpeechGateway.offerHvac(from: Float?): String {
        if (from == null) return NudgeOutcome.CANCELLED
        val to = NudgePolicy.hvacTarget(from)
        if (!speak("실내 설정 온도가 ${setpoint(from)}예요. ${setpoint(to)}로 낮출까요?")) return NudgeOutcome.SPEECH_FAILED

        when (VoiceIntentParser.parse(listen())) {
            VoiceIntent.Affirm -> Unit
            VoiceIntent.Deny, VoiceIntent.Cancel -> {
                speak("알겠어요.")
                return NudgeOutcome.REJECTED
            }
            else -> return NudgeOutcome.NO_RESPONSE
        }

        val now = vehicle.readCare().hvac
        if (car.isParked() != true || now.driverSetpointC != from || now.powerOn != true || now.acOn != false || now.defrostOn != false) {
            return NudgeOutcome.CANCELLED
        }
        val ok = vehicle.setDriverTemperature(to)
        speak(if (ok) "${setpoint(to)}로 낮췄어요." else "온도를 바꾸지 못했어요.")
        return if (ok) NudgeOutcome.EXECUTED else NudgeOutcome.FAILED
    }

    /** 설정 온도는 0.5도 단위라 반올림하지 않는다. */
    private fun setpoint(c: Float): String = if (c % 1f == 0f) "${c.toInt()}도" else "${c}도"

    /** 안내한 장치·단계(핸들 열선 1단)만 실행한다. 공조·시트는 건드리지 않음 (PRD 기능 5 ①). */
    private suspend fun SpeechGateway.offerHeat(tempC: Float?): String {
        val temp = tempC?.let { "오늘 ${spokenTemp(it)}예요. " } ?: ""
        if (!speak("${temp}핸들 열선을 1단으로 켤까요?")) return NudgeOutcome.SPEECH_FAILED

        when (VoiceIntentParser.parse(listen())) {
            VoiceIntent.Affirm -> Unit
            VoiceIntent.Deny, VoiceIntent.Cancel -> {
                speak("알겠어요.")
                return NudgeOutcome.REJECTED
            }
            else -> return NudgeOutcome.NO_RESPONSE
        }

        // 실행 직전 다시 확인: 그사이 출발했거나 직접 켰으면 실행하지 않는다.
        if (car.isParked() != true || vehicle.readCare().steeringHeatLevel != 0) return NudgeOutcome.CANCELLED
        val ok = vehicle.setSteeringHeat(HEAT_LEVEL)
        speak(if (ok) "핸들 열선을 1단으로 켰어요." else "열선을 켜지 못했어요.")
        return if (ok) NudgeOutcome.EXECUTED else NudgeOutcome.FAILED
    }

    private fun spoken(ok: Boolean) = if (ok) NudgeOutcome.SPOKEN else NudgeOutcome.SPEECH_FAILED

    private fun spokenTemp(c: Float): String {
        val n = c.roundToInt()
        return if (n < 0) "영하 ${abs(n)}도" else "${n}도"
    }

    private companion object {
        const val TAG = "NudgeEngine"
        const val HEAT_LEVEL = 1
    }
}
