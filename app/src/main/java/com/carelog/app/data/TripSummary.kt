package com.carelog.app.data

/**
 * 오늘 주행 요약 (PRD 5.3, 기능 3). 오늘 저장한 스냅샷만 쓴다.
 * 값이 부족하면 0이 아니라 null로 두고, 전비를 못 내면 사유를 남긴다.
 */
data class TripSummary(
    val distanceKm: Float?,
    /** 배터리 감소분의 합 (kWh). 충전으로 늘어난 구간은 더하지 않는다. */
    val consumedKwh: Float?,
    val kmPerKwh: Float?,
    val efficiencyNote: String?,
    /** 스냅샷이 끊기지 않고 이어진 구간의 합 (분). */
    val operatingMinutes: Int?,
    /** 앱 시작이 아닌데 기록이 끊긴 곳. 그 구간은 운행 시간에서 빠진다. */
    val gapCount: Int,
) {
    companion object {
        /** [snapshots]는 오늘 것, takenAt 오름차순. */
        fun of(snapshots: List<SnapshotEntity>): TripSummary {
            val km = snapshots.mapNotNull { it.odometer }
            val distance = if (km.size >= 2) (km.last() - km.first()).takeIf { it >= 0f } else null

            val wh = snapshots.mapNotNull { it.evBatteryLevel }
            val consumed = if (wh.size >= 2) wh.zipWithNext { a, b -> maxOf(0f, a - b) }.sum() / WH_PER_KWH else null

            val (efficiency, note) = when {
                snapshots.size < 2 -> null to null
                wh.isEmpty() && snapshots.any { it.fuelPercent != null } -> null to "연료량(L)을 받을 수 없어 연비는 계산하지 않아요."
                distance == null || consumed == null -> null to "주행거리나 배터리 값을 받지 못했어요."
                distance < MIN_DISTANCE_KM || consumed < MIN_CONSUMED_KWH -> null to "주행이 짧아 아직 계산하지 않아요."
                distance / consumed > MAX_PLAUSIBLE_KM_PER_KWH -> null to "주행거리와 배터리 값이 맞지 않아 계산하지 않았어요."
                else -> distance / consumed to null
            }

            var operatingMs = 0L
            var gaps = 0
            snapshots.zipWithNext { a, b ->
                val step = b.takenAt - a.takenAt
                when {
                    b.reason == SnapshotReason.APP_START -> Unit // 새 운행 경계: 앞 구간과 잇지 않는다
                    step <= MAX_STEP_MS -> operatingMs += step
                    else -> gaps++
                }
            }

            return TripSummary(
                distanceKm = distance,
                consumedKwh = consumed,
                kmPerKwh = efficiency,
                efficiencyNote = note,
                operatingMinutes = if (snapshots.size >= 2) (operatingMs / 60_000).toInt() else null,
                gapCount = gaps,
            )
        }

        private const val WH_PER_KWH = 1000f
        private const val MIN_DISTANCE_KM = 1f
        private const val MIN_CONSUMED_KWH = 0.5f

        /** 승용 EV 전비는 대개 4~8 km/kWh. 이보다 훨씬 높으면 값 누락·지연으로 보고 표시하지 않는다. */
        private const val MAX_PLAUSIBLE_KM_PER_KWH = 15f

        /** 체크포인트 간격(1분)의 3배를 넘게 비면 기록 공백으로 본다. */
        private const val MAX_STEP_MS = 180_000L
    }
}
