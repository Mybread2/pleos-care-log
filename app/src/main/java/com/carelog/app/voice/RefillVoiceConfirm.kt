package com.carelog.app.voice

import android.util.Log
import com.carelog.app.core.EnergyType
import com.carelog.app.data.CareLogDao
import com.carelog.app.data.Confirmation
import com.carelog.app.data.RefillRecordEntity
import com.carelog.app.vehicle.CarState

/**
 * 추정 저장한 주유·충전 기록을 음성으로 확인한다 (PRD 6.3, 시나리오 A·B).
 * 무응답·미인식·거절·취소·음성 오류는 추정 기록을 그대로 둔다 (UNCONFIRMED).
 */
class RefillVoiceConfirm(
    private val dao: CareLogDao,
    private val speech: SpeechGateway,
    private val car: CarState,
) {
    suspend fun confirm(record: RefillRecordEntity) = speech.conversation {
        // 주행 중 음성은 정책 검증 전 비활성 (PRD 4장). 화면에서 나중에 확인.
        if (car.isParked() != true) return@conversation log(record, "skipped: not parked")
        if (!speak(question(record))) return@conversation log(record, "speech failed")

        val intent = VoiceIntentParser.parse(listen())
        when (intent) {
            VoiceIntent.Affirm -> {
                dao.updateConfirmation(record.id, Confirmation.CONFIRMED)
                speak("확인했어요.")
            }
            VoiceIntent.NotRefill -> {
                dao.updateConfirmation(record.id, Confirmation.FALSE_POSITIVE)
                speak("이 기록은 집계에서 뺄게요.")
            }
            is VoiceIntent.Amount -> {
                dao.updateQuantity(record.id, intent.value, Confirmation.CONFIRMED)
                speak("${number(intent.value)}${unit(record)}로 고쳐 저장했어요.")
            }
            else -> Unit
        }
        log(record, "intent=$intent")
    }

    private fun question(r: RefillRecordEntity): String {
        val kwh = r.batteryAddedKwh
        return when {
            kwh != null -> "약 ${number(kwh)}킬로와트시로 추정해 저장했어요. 수량이 맞나요?"
            r.type == EnergyType.EV.name -> "충전량을 ${r.beforePercent}퍼센트에서 ${r.afterPercent}퍼센트로 추정해 저장했어요. 맞나요?"
            // 탱크 용량을 알 수 없어 리터 추정이 없다. 실제 수량은 사용자가 말해 주면 기록.
            else -> "연료가 ${r.beforePercent}퍼센트에서 ${r.afterPercent}퍼센트로 늘어 주유로 저장했어요. 맞나요?"
        }
    }

    private fun unit(r: RefillRecordEntity) = if (r.type == EnergyType.EV.name) "킬로와트시" else "리터"

    private fun number(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

    private fun log(r: RefillRecordEntity, msg: String) = Log.i("RefillVoiceConfirm", "record ${r.id}: $msg")
}
