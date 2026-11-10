package com.carelog.app.nudge

/** 케어 넛지 종류 (PRD v2.7 기능 5 카탈로그). convenience = 편의 넛지 빈도 제한 대상. */
enum class NudgeId(val convenience: Boolean) {
    /** ① 겨울 핸들 열선 (EV) — 명시적 수락 시에만 실행 */
    WINTER_HEAT(true),
    /** ② 창문 열고 하차 방지 — 안내만 (닫기는 안전 조건 미검증) */
    WINDOW_OPEN(false),
    /** ④ 결빙 가능성 안내 — 짧은 안내만 */
    ICY_ROAD(false),
    /** ⑤ 공조 조정 — 난방 중 높은 설정 온도를 낮추자고 제안. 명시적 수락 시에만 실행, 올리는 변경은 하지 않음 */
    HVAC_ADJUST(true),
}

object NudgeOutcome {
    const val SPOKEN = "SPOKEN"
    const val EXECUTED = "EXECUTED"
    const val REJECTED = "REJECTED"
    const val NO_RESPONSE = "NO_RESPONSE"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
    const val SPEECH_FAILED = "SPEECH_FAILED"
    /** 스마트 자동 케어: 사전 동의 범위 안에서 묻지 않고 실행 (PRD 6.3) */
    const val AUTO_EXECUTED = "AUTO_EXECUTED"
}

/** 운전석 공조 상태. 값을 하나라도 모르면 공조 조정은 보류한다 (PRD 기능 5 ⑤: 판단 불가 시 보류). */
data class HvacState(
    val powerOn: Boolean?,
    val acOn: Boolean?,
    /** 앞·뒤 유리 제상 중 하나라도 켜져 있으면 true */
    val defrostOn: Boolean?,
    val driverSetpointC: Float?,
)

data class NudgeContext(
    val now: Long,
    val nudgeEnabled: Boolean,
    /** null = 판정 불가 → 주차로 보지 않음 */
    val parked: Boolean?,
    val isEv: Boolean,
    val outsideTempC: Float?,
    val steeringHeatLevel: Int?,
    val driverBeltBuckled: Boolean?,
    val previousBeltBuckled: Boolean?,
    val anyWindowOpen: Boolean?,
    /** 마지막 음성 대화(충전 확인 포함)가 끝난 시각 */
    val lastVoiceAt: Long? = null,
    val hvac: HvacState? = null,
    /** 편의 넛지 최소 간격. null = 편의 넛지 끄기 (PRD 4장 설정) */
    val convenienceGapMs: Long? = NudgePolicy.CONVENIENCE_MIN_GAP_MS,
)

data class NudgeEvent(val nudge: NudgeId, val at: Long, val outcome: String)

/**
 * 지금 어떤 넛지를 낼지 판단한다. 수치는 초기값이며 팀 합의로 조정.
 * - 정차 중에만 (주행 중 음성은 정책 검증 전 비활성, PRD 4장)
 * - 편의 넛지: 한 주행 최대 2회, 최소 15분 간격(설정 30분·끄기), 발화한 제안은 수락 여부와 무관하게 횟수에 포함
 * - 거절·실행·실패한 편의 넛지는 그 주행 동안 다시 묻지 않음 (사용자 의사 우선, PRD 6.3)
 * - 다른 음성 대화(결빙 안내·충전 확인) 직후에는 편의 제안을 붙이지 않음
 * - 공조 조정: 난방 중(공조 On·에어컨 Off·제상 Off)이고 설정 온도가 높을 때만, 낮추는 쪽으로만 제안
 */
object NudgePolicy {
    const val HEAT_MAX_TEMP_C = 5f
    const val ICY_MAX_TEMP_C = 3f
    const val HVAC_MAX_OUTSIDE_C = 10f
    const val HVAC_HIGH_SETPOINT_C = 24f
    const val HVAC_STEP_DOWN_C = 2f
    const val CONVENIENCE_MAX_PER_TRIP = 2
    const val CONVENIENCE_MIN_GAP_MS = 15 * 60_000L
    const val GAP_AFTER_VOICE_MS = 2 * 60_000L
    const val WINDOW_REPEAT_GAP_MS = 10 * 60_000L

    private val SUPPRESSING = setOf(NudgeOutcome.REJECTED, NudgeOutcome.EXECUTED, NudgeOutcome.AUTO_EXECUTED, NudgeOutcome.FAILED)

    fun decide(ctx: NudgeContext, tripEvents: List<NudgeEvent>): NudgeId? {
        if (!ctx.nudgeEnabled || ctx.parked != true) return null

        val beltJustReleased = ctx.previousBeltBuckled == true && ctx.driverBeltBuckled == false
        if (beltJustReleased && ctx.anyWindowOpen == true &&
            tripEvents.none { it.nudge == NudgeId.WINDOW_OPEN && ctx.now - it.at < WINDOW_REPEAT_GAP_MS }
        ) return NudgeId.WINDOW_OPEN

        val temp = ctx.outsideTempC ?: return null
        if (temp <= ICY_MAX_TEMP_C && tripEvents.none { it.nudge == NudgeId.ICY_ROAD }) return NudgeId.ICY_ROAD

        if (ctx.isEv && temp <= HEAT_MAX_TEMP_C && ctx.steeringHeatLevel == 0 &&
            convenienceAllowed(NudgeId.WINTER_HEAT, ctx, tripEvents)
        ) return NudgeId.WINTER_HEAT

        val hvac = ctx.hvac
        val setpoint = hvac?.driverSetpointC
        if (temp <= HVAC_MAX_OUTSIDE_C && hvac?.powerOn == true && hvac.acOn == false && hvac.defrostOn == false &&
            setpoint != null && setpoint >= HVAC_HIGH_SETPOINT_C &&
            convenienceAllowed(NudgeId.HVAC_ADJUST, ctx, tripEvents)
        ) return NudgeId.HVAC_ADJUST

        return null
    }

    /** 제안할 목표 온도. 항상 현재보다 낮다. */
    fun hvacTarget(setpointC: Float): Float = setpointC - HVAC_STEP_DOWN_C

    /** 편의 넛지 공통: 횟수·간격 한도, 같은 항목 거절·실행·실패 이력 없음, 직전 음성 대화와 간격. */
    private fun convenienceAllowed(id: NudgeId, ctx: NudgeContext, events: List<NudgeEvent>): Boolean =
        ctx.convenienceGapMs != null && convenienceAllowed(ctx.now, ctx.convenienceGapMs, events) &&
            events.none { it.nudge == id && it.outcome in SUPPRESSING } &&
            ctx.now - lastVoiceAt(ctx, events) >= GAP_AFTER_VOICE_MS

    private fun lastVoiceAt(ctx: NudgeContext, events: List<NudgeEvent>): Long =
        maxOf(ctx.lastVoiceAt ?: 0L, events.maxOfOrNull { it.at } ?: 0L)

    private fun convenienceAllowed(now: Long, gapMs: Long, events: List<NudgeEvent>): Boolean {
        val conv = events.filter { it.nudge.convenience }
        return conv.size < CONVENIENCE_MAX_PER_TRIP && conv.all { now - it.at >= gapMs }
    }
}
