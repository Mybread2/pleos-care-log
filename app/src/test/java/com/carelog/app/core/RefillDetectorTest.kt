package com.carelog.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefillDetectorTest {

    private fun reading(t: Long, fuel: Int? = null, soc: Int? = null, odo: Float? = null, wh: Float? = null) =
        EnergyReading(takenAt = t, fuelPercent = fuel, socPercent = soc, odometer = odo, evBatteryLevelWh = wh)

    @Test
    fun `충전량은 배터리 잔량 Wh 차이로 계산`() {
        val result = RefillDetector.detect(reading(1, soc = 20, wh = 15_500f), reading(2, soc = 80, wh = 61_900f))
        assertEquals(46.4f, result.single().batteryAddedKwh!!, 0.01f)
    }

    @Test
    fun `잔량 값이 없으면 충전량은 비움`() {
        assertNull(RefillDetector.detect(reading(1, soc = 20), reading(2, soc = 80, wh = 61_900f)).single().batteryAddedKwh)
    }

    @Test
    fun `SoC는 늘었는데 잔량이 줄면 충전량은 비움`() {
        assertNull(RefillDetector.detect(reading(1, soc = 20, wh = 50_000f), reading(2, soc = 80, wh = 40_000f)).single().batteryAddedKwh)
    }

    @Test
    fun `연료가 임계값 이상 늘면 주유 후보`() {
        val result = RefillDetector.detect(reading(1, fuel = 20, odo = 100f), reading(2, fuel = 80, odo = 100f))
        assertEquals(listOf(RefillCandidate(EnergyType.FUEL, 20, 80)), result)
        assertEquals(60, result.single().deltaPercent)
    }

    @Test
    fun `배터리가 임계값 이상 늘면 충전 후보`() {
        val result = RefillDetector.detect(reading(1, soc = 30), reading(2, soc = 33))
        assertEquals(listOf(RefillCandidate(EnergyType.EV, 30, 33)), result)
    }

    @Test
    fun `임계값 미만 흔들림은 무시`() {
        assertTrue(RefillDetector.detect(reading(1, fuel = 50, soc = 50), reading(2, fuel = 54, soc = 52)).isEmpty())
    }

    @Test
    fun `잔량 감소는 후보 아님`() {
        assertTrue(RefillDetector.detect(reading(1, fuel = 80), reading(2, fuel = 20)).isEmpty())
    }

    @Test
    fun `값이 없으면 추정하지 않음`() {
        assertTrue(RefillDetector.detect(reading(1, fuel = null), reading(2, fuel = 80)).isEmpty())
        assertTrue(RefillDetector.detect(reading(1, fuel = 20), reading(2, fuel = null)).isEmpty())
    }

    @Test
    fun `범위를 벗어난 값은 제외`() {
        assertTrue(RefillDetector.detect(reading(1, fuel = 20), reading(2, fuel = 120)).isEmpty())
        assertTrue(RefillDetector.detect(reading(1, soc = -1), reading(2, soc = 50)).isEmpty())
    }

    @Test
    fun `누적 주행거리가 역전되면 계산하지 않음`() {
        assertTrue(RefillDetector.detect(reading(1, fuel = 20, odo = 500f), reading(2, fuel = 80, odo = 400f)).isEmpty())
    }

    @Test
    fun `시간이 거꾸로면 계산하지 않음`() {
        assertTrue(RefillDetector.detect(reading(2, fuel = 20), reading(1, fuel = 80)).isEmpty())
    }
}
