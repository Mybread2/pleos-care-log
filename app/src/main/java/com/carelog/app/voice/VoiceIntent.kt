package com.carelog.app.voice

sealed interface VoiceIntent {
    data object Affirm : VoiceIntent
    data object Deny : VoiceIntent
    data object Cancel : VoiceIntent
    /** "충전 안 했어" — 오탐 (PRD 시나리오 B). */
    data object NotRefill : VoiceIntent
    /** "나 34L 넣었어" — 수량 교정. 단위는 기록 유형(EV=kWh, 연료=L)을 따른다. */
    data class Amount(val value: Float) : VoiceIntent
    data object Unknown : VoiceIntent
}

/**
 * 짧은 한국어 응답을 의도로 바꾼다. 무응답·미인식은 Unknown (제어 동의 아님, PRD 6.3).
 * 순서: 취소 > 오탐 > 수량 > 거절 > 수락.
 */
object VoiceIntentParser {
    private val CANCEL = listOf("취소", "그만")
    private val NOT_REFILL = listOf("안했", "안넣", "충전한게아니", "주유한게아니", "넣은게아니")
    private val DENY_PREFIX = listOf("아니", "노", "싫")
    private val DENY_CONTAINS = listOf("싫어", "괜찮아", "됐어", "안켜", "하지마", "필요없")
    private val AFFIRM_EXACT = setOf("응", "어", "네", "예", "웅", "엉", "ㅇㅇ")
    private val AFFIRM_PREFIX = listOf("응", "네", "예", "맞아", "맞습", "맞네", "그래", "좋아", "켜", "부탁", "오케이", "그렇게")
    private val NUMBER = Regex("""\d+(?:\.\d+)?""")

    fun parse(text: String?): VoiceIntent {
        // 소수점(숫자 사이의 '.')은 남긴다 — "40.5"가 405가 되면 안 됨
        val t = text?.replace(Regex("""[\s,!?~]|\.(?!\d)"""), "") ?: return VoiceIntent.Unknown
        if (t.isEmpty()) return VoiceIntent.Unknown
        return when {
            CANCEL.any { it in t } -> VoiceIntent.Cancel
            NOT_REFILL.any { it in t } -> VoiceIntent.NotRefill
            NUMBER.find(t) != null -> VoiceIntent.Amount(NUMBER.find(t)!!.value.toFloat())
            DENY_PREFIX.any { t.startsWith(it) } || DENY_CONTAINS.any { it in t } -> VoiceIntent.Deny
            t in AFFIRM_EXACT || AFFIRM_PREFIX.any { t.startsWith(it) } -> VoiceIntent.Affirm
            else -> VoiceIntent.Unknown
        }
    }
}
