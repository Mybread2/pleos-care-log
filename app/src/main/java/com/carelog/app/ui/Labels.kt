package com.carelog.app.ui

import com.carelog.app.core.EnergyType
import com.carelog.app.data.ConsumableStatus
import com.carelog.app.data.RefillRecordEntity
import com.carelog.app.nudge.NudgeId
import com.carelog.app.nudge.NudgeOutcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 화면 문구 모음. 값이 없으면 0이 아니라 "—"로 표시한다 (PRD 5.3). */
object Labels {
    private val dateTime = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)

    fun time(millis: Long): String = dateTime.format(Date(millis))

    fun won(value: Int?): String = value?.let { "%,d원".format(it) } ?: "—"

    fun km(value: Float?): String = value?.let { "%,.1f km".format(it) } ?: "—"

    fun kwh(value: Float?): String = value?.let { "%.1f kWh".format(it) } ?: "—"

    fun minutes(value: Int?): String = when {
        value == null -> "—"
        value >= 60 -> "${value / 60}시간 ${value % 60}분"
        else -> "${value}분"
    }

    private val date = SimpleDateFormat("M월 d일", Locale.KOREA)
    private val month = SimpleDateFormat("yyyy년 M월", Locale.KOREA)

    /** 예: "11월 9일 교체 · 약 15,000 km 남음 · 2027년 11월까지" */
    fun consumable(s: ConsumableStatus): String {
        val resetAt = s.resetAt ?: return "기준 미설정 — 교체했다면 [교체함]을 눌러 주세요"
        val km = s.remainingKm?.roundToInt()?.let { if (it > 0) "약 %,d km 남음".format(it) else "주기 %,d km 지남".format(-it) }
        val due = s.dueAt?.let { "${month.format(Date(it))}까지" }
        return listOfNotNull("${date.format(Date(resetAt))} 교체", km, due).joinToString(" · ")
    }

    fun isEv(r: RefillRecordEntity) = r.type == EnergyType.EV.name

    fun refillName(r: RefillRecordEntity) = if (isEv(r)) "충전" else "주유"

    fun quantityUnit(r: RefillRecordEntity) = if (isEv(r)) "kWh" else "L"

    fun nudge(name: String): String = when (runCatching { NudgeId.valueOf(name) }.getOrNull()) {
        NudgeId.WINTER_HEAT -> "핸들 열선 제안"
        NudgeId.WINDOW_OPEN -> "창문 열림 안내"
        NudgeId.ICY_ROAD -> "결빙 가능성 안내"
        NudgeId.HVAC_ADJUST -> "공조 온도 제안"
        null -> name
    }

    fun outcome(outcome: String): String = when (outcome) {
        NudgeOutcome.SPOKEN -> "안내함"
        NudgeOutcome.EXECUTED -> "수락 → 실행"
        NudgeOutcome.REJECTED -> "거절"
        NudgeOutcome.NO_RESPONSE -> "응답 없음 (실행 안 함)"
        NudgeOutcome.FAILED -> "실행 실패"
        NudgeOutcome.CANCELLED -> "상태가 바뀌어 취소"
        NudgeOutcome.SPEECH_FAILED -> "음성 출력 실패"
        NudgeOutcome.AUTO_EXECUTED -> "자동 케어 실행"
        else -> outcome
    }
}
