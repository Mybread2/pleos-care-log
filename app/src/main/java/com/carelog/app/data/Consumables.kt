package com.carelog.app.data

import java.util.Calendar

/**
 * 소모품과 교체 주기 (PRD 기능 3·4). 주기는 차종 설명서를 확인하기 전 예시값이라 화면에도 그렇게 표시한다.
 */
enum class Consumable(val label: String, val intervalKm: Int, val intervalMonths: Int?, val iceOnly: Boolean) {
    ENGINE_OIL("엔진오일", 10_000, 12, iceOnly = true),
    CABIN_FILTER("에어컨 필터", 15_000, 12, iceOnly = false),
    TIRE_ROTATION("타이어 위치 교환", 10_000, null, iceOnly = false),
}

data class ConsumableStatus(
    val item: Consumable,
    /** 사용자가 교체했다고 누른 시각. 없으면 기준 미설정. */
    val resetAt: Long?,
    /** 기준점이나 현재 주행거리를 모르면 null — 남은 거리를 단정하지 않는다. */
    val remainingKm: Float?,
    val dueAt: Long?,
) {
    fun isDue(now: Long) = (remainingKm != null && remainingKm <= 0f) || (dueAt != null && now >= dueAt)
}

object ConsumableSchedule {
    fun status(item: Consumable, lastReset: ConsumableResetEntity?, odometerKm: Float?): ConsumableStatus {
        val baseKm = lastReset?.odometerKm
        val remaining = if (baseKm != null && odometerKm != null) item.intervalKm - (odometerKm - baseKm) else null
        val due = if (lastReset != null && item.intervalMonths != null) {
            Calendar.getInstance().apply { timeInMillis = lastReset.at; add(Calendar.MONTH, item.intervalMonths) }.timeInMillis
        } else null
        return ConsumableStatus(item, lastReset?.at, remaining, due)
    }

    /** EV가 확실하면 엔진오일은 뺀다. 파워트레인을 모르면 모두 보여준다. */
    fun all(resets: List<ConsumableResetEntity>, odometerKm: Float?, isEv: Boolean?): List<ConsumableStatus> =
        Consumable.entries
            .filter { !(it.iceOnly && isEv == true) }
            .map { item -> status(item, resets.filter { it.item == item.name }.maxByOrNull { it.at }, odometerKm) }
}
