package com.carelog.app.data

/**
 * 이번 달 요약. 오탐은 집계에서 빼고, 값이 없는 항목은 0이 아니라 null (PRD 5.3, 기능 4).
 */
data class MonthlySummary(
    val refillCount: Int,
    val batteryAddedKwh: Float?,
    val totalCostWon: Int?,
    val missingCostCount: Int,
    val distanceKm: Float?,
) {
    companion object {
        fun of(records: List<RefillRecordEntity>, monthStart: Long, odometer: OdometerRange?): MonthlySummary {
            val counted = records.filter {
                it.detectedAt >= monthStart && it.confirmation != Confirmation.FALSE_POSITIVE
            }
            val kwh = counted.mapNotNull { it.batteryAddedKwh }
            val costs = counted.mapNotNull { it.costWon }
            val min = odometer?.minKm
            val max = odometer?.maxKm
            return MonthlySummary(
                refillCount = counted.size,
                batteryAddedKwh = kwh.takeIf { it.isNotEmpty() }?.sum(),
                totalCostWon = costs.takeIf { it.isNotEmpty() }?.sum(),
                missingCostCount = counted.count { it.costWon == null },
                distanceKm = if (min != null && max != null) max - min else null,
            )
        }
    }
}
