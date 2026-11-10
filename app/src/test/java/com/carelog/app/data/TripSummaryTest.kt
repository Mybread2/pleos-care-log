package com.carelog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TripSummaryTest {
    private val min = 60_000L

    private fun snap(minute: Int, km: Float?, wh: Float?, reason: String = SnapshotReason.PERIODIC, fuel: Int? = null) =
        SnapshotEntity(
            takenAt = minute * min, reason = reason, fuelPercent = fuel,
            socPercent = if (wh != null) 50 else null, evBatteryLevel = wh, odometer = km,
        )

    @Test
    fun `주행거리·전비·운행시간 계산`() {
        val s = TripSummary.of(
            listOf(
                snap(0, 1000f, 40_000f, SnapshotReason.APP_START),
                snap(1, 1005f, 39_000f),
                snap(2, 1010f, 38_000f),
            ),
        )
        assertEquals(10f, s.distanceKm!!, 0.01f)
        assertEquals(2f, s.consumedKwh!!, 0.01f)
        assertEquals(5f, s.kmPerKwh!!, 0.01f)
        assertEquals(2, s.operatingMinutes)
        assertEquals(0, s.gapCount)
    }

    @Test
    fun `충전으로 늘어난 구간은 소비량에 넣지 않음`() {
        val s = TripSummary.of(listOf(snap(0, 1000f, 40_000f), snap(1, 1000f, 60_000f), snap(2, 1010f, 58_000f)))
        assertEquals(2f, s.consumedKwh!!, 0.01f)
    }

    @Test
    fun `기록이 끊긴 구간은 운행시간에서 빼고 공백으로 셈`() {
        val s = TripSummary.of(listOf(snap(0, 1000f, null), snap(1, 1001f, null), snap(30, 1002f, null), snap(31, 1003f, null)))
        assertEquals(2, s.operatingMinutes)
        assertEquals(1, s.gapCount)
    }

    @Test
    fun `앱 다시 시작은 공백이 아니라 운행 경계`() {
        val s = TripSummary.of(listOf(snap(0, 1000f, null), snap(1, 1001f, null), snap(120, 1001f, null, SnapshotReason.APP_START), snap(121, 1002f, null)))
        assertEquals(2, s.operatingMinutes)
        assertEquals(0, s.gapCount)
    }

    @Test
    fun `짧은 주행은 전비를 내지 않고 사유를 남김`() {
        val s = TripSummary.of(listOf(snap(0, 1000f, 40_000f), snap(1, 1000.5f, 39_900f)))
        assertNull(s.kmPerKwh)
        assertNotNull(s.efficiencyNote)
    }

    @Test
    fun `말이 안 되는 전비는 표시하지 않음`() {
        val s = TripSummary.of(listOf(snap(0, 1000f, 40_000f), snap(1, 1160f, 36_500f)))
        assertNull(s.kmPerKwh)
        assertEquals("주행거리와 배터리 값이 맞지 않아 계산하지 않았어요.", s.efficiencyNote)
    }

    @Test
    fun `내연기관은 연비를 계산하지 않음`() {
        val s = TripSummary.of(listOf(snap(0, 1000f, null, fuel = 60), snap(1, 1020f, null, fuel = 58)))
        assertEquals(20f, s.distanceKm!!, 0.01f)
        assertNull(s.kmPerKwh)
        assertEquals("연료량(L)을 받을 수 없어 연비는 계산하지 않아요.", s.efficiencyNote)
    }

    @Test
    fun `스냅샷이 하나뿐이면 모두 공란`() {
        val s = TripSummary.of(listOf(snap(0, 1000f, 40_000f)))
        assertNull(s.distanceKm)
        assertNull(s.operatingMinutes)
        assertNull(s.efficiencyNote)
    }
}
