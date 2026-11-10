package com.carelog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ConsumablesTest {
    private fun reset(item: Consumable, at: Long, km: Float?) = ConsumableResetEntity(item = item.name, at = at, odometerKm = km)

    @Test
    fun `기준점이 없으면 남은 거리를 단정하지 않음`() {
        val s = ConsumableSchedule.status(Consumable.CABIN_FILTER, null, 12_000f)
        assertNull(s.resetAt)
        assertNull(s.remainingKm)
        assertNull(s.dueAt)
        assertFalse(s.isDue(System.currentTimeMillis()))
    }

    @Test
    fun `교체 시점 주행거리 기준으로 남은 거리 계산`() {
        val s = ConsumableSchedule.status(Consumable.CABIN_FILTER, reset(Consumable.CABIN_FILTER, 0, 10_000f), 12_500f)
        assertEquals(12_500f, s.remainingKm!!, 0.01f)
    }

    @Test
    fun `교체 시점 주행거리를 몰랐으면 기간만 계산`() {
        val at = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 15, 9, 0, 0) }.timeInMillis
        val s = ConsumableSchedule.status(Consumable.CABIN_FILTER, reset(Consumable.CABIN_FILTER, at, null), 12_500f)
        assertNull(s.remainingKm)
        val due = Calendar.getInstance().apply { timeInMillis = s.dueAt!! }
        assertEquals(2027, due.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, due.get(Calendar.MONTH))
    }

    @Test
    fun `주기를 넘기면 교체 시기`() {
        val s = ConsumableSchedule.status(Consumable.TIRE_ROTATION, reset(Consumable.TIRE_ROTATION, 0, 1_000f), 11_200f)
        assertTrue(s.isDue(0))
    }

    @Test
    fun `가장 최근 교체 기록을 기준으로 씀`() {
        val list = ConsumableSchedule.all(
            listOf(reset(Consumable.TIRE_ROTATION, 100, 1_000f), reset(Consumable.TIRE_ROTATION, 200, 9_000f)),
            odometerKm = 10_000f, isEv = true,
        )
        assertEquals(9_000f, list.first { it.item == Consumable.TIRE_ROTATION }.remainingKm!!, 0.01f)
    }

    @Test
    fun `EV면 엔진오일 제외, 파워트레인을 모르면 모두 표시`() {
        assertFalse(ConsumableSchedule.all(emptyList(), null, isEv = true).any { it.item == Consumable.ENGINE_OIL })
        assertEquals(Consumable.entries.size, ConsumableSchedule.all(emptyList(), null, isEv = null).size)
    }
}
