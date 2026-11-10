package com.carelog.app.core

/** 한 시점의 차량 에너지 관측값. 받지 못한 값은 null (0으로 채우지 않음). */
data class EnergyReading(
    val takenAt: Long,
    val fuelPercent: Int?,
    val socPercent: Int?,
    val odometer: Float?,
    val evBatteryLevelWh: Float? = null,
)

enum class EnergyType { FUEL, EV }

data class RefillCandidate(
    val type: EnergyType,
    val beforePercent: Int,
    val afterPercent: Int,
    /** 배터리 에너지 순증량. 충전기 과금량과 다름 (PRD 6.5). 근거 없으면 null. */
    val batteryAddedKwh: Float? = null,
) {
    val deltaPercent: Int get() = afterPercent - beforePercent
}

/**
 * 직전 스냅샷과 현재 스냅샷의 잔량 차분으로 주유·충전 후보를 찾는다 (PRD 6.5).
 * 임계값은 초기 제안값이며 차량별 시험으로 조정한다.
 */
object RefillDetector {
    const val FUEL_MIN_DELTA = 5
    const val SOC_MIN_DELTA = 3

    fun detect(prev: EnergyReading, curr: EnergyReading): List<RefillCandidate> {
        if (curr.takenAt <= prev.takenAt) return emptyList()
        if (odometerReversed(prev.odometer, curr.odometer)) return emptyList()

        return listOfNotNull(
            candidate(EnergyType.FUEL, prev.fuelPercent, curr.fuelPercent, FUEL_MIN_DELTA),
            candidate(EnergyType.EV, prev.socPercent, curr.socPercent, SOC_MIN_DELTA)
                ?.copy(batteryAddedKwh = addedKwh(prev.evBatteryLevelWh, curr.evBatteryLevelWh)),
        )
    }

    private fun odometerReversed(prev: Float?, curr: Float?): Boolean =
        prev != null && curr != null && curr < prev

    /** SoC는 늘었는데 잔량이 줄었거나 같으면 근거가 모순되므로 null. */
    private fun addedKwh(beforeWh: Float?, afterWh: Float?): Float? {
        if (beforeWh == null || afterWh == null || afterWh <= beforeWh) return null
        return (afterWh - beforeWh) / 1000f
    }

    private fun candidate(type: EnergyType, before: Int?, after: Int?, minDelta: Int): RefillCandidate? {
        if (before == null || after == null) return null
        if (before !in 0..100 || after !in 0..100) return null
        if (after - before < minDelta) return null
        return RefillCandidate(type, before, after)
    }
}
