package com.carelog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlySummaryTest {
    private val monthStart = 1_000L

    private fun record(at: Long, kwh: Float? = null, cost: Int? = null, status: String = Confirmation.UNCONFIRMED) =
        RefillRecordEntity(
            detectedAt = at, type = "EV", beforePercent = 20, afterPercent = 80,
            beforeSnapshotId = 1, afterSnapshotId = at, batteryAddedKwh = kwh, costWon = cost, confirmation = status,
        )

    @Test
    fun `오탐과 지난달 기록은 집계에서 제외`() {
        val s = MonthlySummary.of(
            listOf(
                record(2_000, kwh = 40f, cost = 10_000),
                record(3_000, kwh = 30f, cost = 5_000, status = Confirmation.FALSE_POSITIVE),
                record(500, kwh = 20f, cost = 7_000),
            ),
            monthStart, null,
        )
        assertEquals(1, s.refillCount)
        assertEquals(40f, s.batteryAddedKwh!!, 0.01f)
        assertEquals(10_000, s.totalCostWon)
    }

    @Test
    fun `금액이 없으면 0원으로 합산하지 않음`() {
        val s = MonthlySummary.of(listOf(record(2_000), record(3_000)), monthStart, null)
        assertNull(s.totalCostWon)
        assertNull(s.batteryAddedKwh)
        assertEquals(2, s.missingCostCount)
    }

    @Test
    fun `일부만 금액이 있으면 입력분만 합산하고 미입력 건수 표시`() {
        val s = MonthlySummary.of(listOf(record(2_000, cost = 30_000), record(3_000)), monthStart, null)
        assertEquals(30_000, s.totalCostWon)
        assertEquals(1, s.missingCostCount)
    }

    @Test
    fun `주행거리는 이번 달 누적 주행거리 최대-최소`() {
        assertEquals(124.5f, MonthlySummary.of(emptyList(), monthStart, OdometerRange(1000f, 1124.5f)).distanceKm!!, 0.01f)
        assertNull(MonthlySummary.of(emptyList(), monthStart, OdometerRange(null, null)).distanceKm)
    }
}
