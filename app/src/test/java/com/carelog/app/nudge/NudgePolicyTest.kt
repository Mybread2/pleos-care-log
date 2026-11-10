package com.carelog.app.nudge

import com.carelog.app.nudge.NudgeId.ICY_ROAD
import com.carelog.app.nudge.NudgeId.WINDOW_OPEN
import com.carelog.app.nudge.NudgeId.WINTER_HEAT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NudgePolicyTest {
    private val min = 60_000L
    private val t0 = 100 * min

    private fun ctx(
        now: Long = t0, enabled: Boolean = true, parked: Boolean? = true, ev: Boolean = true,
        temp: Float? = 4f, heat: Int? = 0, belt: Boolean? = true, prevBelt: Boolean? = true, window: Boolean? = false,
        lastVoice: Long? = null, hvac: HvacState? = null,
    ) = NudgeContext(now, enabled, parked, ev, temp, heat, belt, prevBelt, window, lastVoice, hvac)

    /** 난방 중 26도 설정. 열선 넛지와 섞이지 않게 내연기관·8도로 둔다. */
    private val heating = HvacState(powerOn = true, acOn = false, defrostOn = false, driverSetpointC = 26f)
    private fun hvacCtx(h: HvacState = heating, now: Long = t0, temp: Float? = 8f) = ctx(now = now, ev = false, temp = temp, hvac = h)

    private fun ev(n: NudgeId, at: Long, outcome: String = NudgeOutcome.NO_RESPONSE) = NudgeEvent(n, at, outcome)

    @Test
    fun `추운 날 EV 정차 중 열선이 꺼져 있으면 열선 제안`() = assertEquals(WINTER_HEAT, NudgePolicy.decide(ctx(), emptyList()))

    @Test
    fun `주차 판정이 없거나 주행 중이면 아무것도 안 함`() {
        assertNull(NudgePolicy.decide(ctx(parked = null), emptyList()))
        assertNull(NudgePolicy.decide(ctx(parked = false), emptyList()))
    }

    @Test
    fun `넛지 설정이 꺼져 있으면 아무것도 안 함`() = assertNull(NudgePolicy.decide(ctx(enabled = false), emptyList()))

    @Test
    fun `외기온을 모르면 온도 기반 넛지 안 함`() = assertNull(NudgePolicy.decide(ctx(temp = null), emptyList()))

    @Test
    fun `열선이 이미 켜져 있거나 상태를 모르면 제안 안 함`() {
        assertNull(NudgePolicy.decide(ctx(heat = 1), emptyList()))
        assertNull(NudgePolicy.decide(ctx(heat = null), emptyList()))
    }

    @Test
    fun `내연기관 차량에는 열선 넛지 안 함`() = assertNull(NudgePolicy.decide(ctx(ev = false), emptyList()))

    @Test
    fun `편의 넛지는 15분 간격`() {
        assertNull(NudgePolicy.decide(ctx(), listOf(ev(WINTER_HEAT, t0 - 14 * min))))
        assertEquals(WINTER_HEAT, NudgePolicy.decide(ctx(), listOf(ev(WINTER_HEAT, t0 - 15 * min))))
    }

    @Test
    fun `편의 넛지는 한 주행 최대 2회`() {
        val events = listOf(ev(WINTER_HEAT, t0 - 60 * min), ev(WINTER_HEAT, t0 - 30 * min))
        assertNull(NudgePolicy.decide(ctx(), events))
    }

    @Test
    fun `30분 간격을 고르면 30분이 지나야 다음 편의 넛지`() {
        val gap30 = ctx().copy(convenienceGapMs = 30 * min)
        assertNull(NudgePolicy.decide(gap30, listOf(ev(WINTER_HEAT, t0 - 20 * min))))
        assertEquals(WINTER_HEAT, NudgePolicy.decide(gap30, listOf(ev(WINTER_HEAT, t0 - 30 * min))))
    }

    @Test
    fun `편의 넛지를 끄면 열선·공조는 안 하고 결빙 안내는 함`() {
        assertNull(NudgePolicy.decide(ctx().copy(convenienceGapMs = null), emptyList()))
        assertNull(NudgePolicy.decide(hvacCtx().copy(convenienceGapMs = null), emptyList()))
        assertEquals(ICY_ROAD, NudgePolicy.decide(ctx(temp = -2f).copy(convenienceGapMs = null), emptyList()))
    }

    @Test
    fun `거절했거나 실행한 열선은 그 주행 동안 다시 묻지 않음`() {
        assertNull(NudgePolicy.decide(ctx(), listOf(ev(WINTER_HEAT, t0 - 60 * min, NudgeOutcome.REJECTED))))
        assertNull(NudgePolicy.decide(ctx(), listOf(ev(WINTER_HEAT, t0 - 60 * min, NudgeOutcome.EXECUTED))))
    }

    @Test
    fun `결빙 안내가 열선보다 먼저, 한 주행 1회`() {
        assertEquals(ICY_ROAD, NudgePolicy.decide(ctx(temp = -2f), emptyList()))
        assertEquals(WINTER_HEAT, NudgePolicy.decide(ctx(temp = -2f), listOf(ev(ICY_ROAD, t0 - 3 * min, NudgeOutcome.SPOKEN))))
    }

    @Test
    fun `결빙 안내 직후에는 열선 제안을 붙이지 않음`() {
        assertNull(NudgePolicy.decide(ctx(temp = -2f), listOf(ev(ICY_ROAD, t0 - 1 * min, NudgeOutcome.SPOKEN))))
    }

    @Test
    fun `충전 확인 대화 직후에는 열선 제안을 붙이지 않음`() {
        assertNull(NudgePolicy.decide(ctx(lastVoice = t0 - 30_000L), emptyList()))
        assertEquals(WINTER_HEAT, NudgePolicy.decide(ctx(lastVoice = t0 - 2 * min), emptyList()))
    }

    @Test
    fun `벨트를 푸는 순간 창문이 열려 있으면 창문 안내`() {
        assertEquals(WINDOW_OPEN, NudgePolicy.decide(ctx(temp = 20f, belt = false, prevBelt = true, window = true), emptyList()))
    }

    @Test
    fun `벨트가 계속 풀려 있으면 창문 안내 반복 안 함`() {
        assertNull(NudgePolicy.decide(ctx(temp = 20f, belt = false, prevBelt = false, window = true), emptyList()))
    }

    @Test
    fun `창문 안내는 10분 안에 다시 안 함`() {
        val c = ctx(temp = 20f, belt = false, prevBelt = true, window = true)
        assertNull(NudgePolicy.decide(c, listOf(ev(WINDOW_OPEN, t0 - 5 * min, NudgeOutcome.SPOKEN))))
    }

    @Test
    fun `난방 중 설정 온도가 높으면 공조 조정 제안, 목표는 낮추는 쪽`() {
        assertEquals(NudgeId.HVAC_ADJUST, NudgePolicy.decide(hvacCtx(), emptyList()))
        assertEquals(24f, NudgePolicy.hvacTarget(26f), 0.01f)
    }

    @Test
    fun `냉방·제상 중이거나 공조 상태를 모르면 보류`() {
        assertNull(NudgePolicy.decide(hvacCtx(heating.copy(acOn = true)), emptyList()))
        assertNull(NudgePolicy.decide(hvacCtx(heating.copy(defrostOn = true)), emptyList()))
        assertNull(NudgePolicy.decide(hvacCtx(heating.copy(defrostOn = null)), emptyList()))
        assertNull(NudgePolicy.decide(hvacCtx(heating.copy(driverSetpointC = null)), emptyList()))
        assertNull(NudgePolicy.decide(hvacCtx(heating.copy(powerOn = false)), emptyList()))
        assertNull(NudgePolicy.decide(ctx(ev = false, temp = 8f, hvac = null), emptyList()))
    }

    @Test
    fun `설정 온도가 적당하거나 바깥이 따뜻하면 제안 안 함`() {
        assertNull(NudgePolicy.decide(hvacCtx(heating.copy(driverSetpointC = 23.5f)), emptyList()))
        assertNull(NudgePolicy.decide(hvacCtx(temp = 18f), emptyList()))
    }

    @Test
    fun `공조 조정은 열선과 편의 넛지 한도를 공유`() {
        assertNull(NudgePolicy.decide(hvacCtx(), listOf(ev(WINTER_HEAT, t0 - 5 * min, NudgeOutcome.EXECUTED))))
        assertEquals(NudgeId.HVAC_ADJUST, NudgePolicy.decide(hvacCtx(), listOf(ev(WINTER_HEAT, t0 - 16 * min, NudgeOutcome.EXECUTED))))
        assertNull(
            NudgePolicy.decide(hvacCtx(), listOf(ev(WINTER_HEAT, t0 - 40 * min), ev(WINTER_HEAT, t0 - 20 * min))),
        )
    }

    @Test
    fun `거절했거나 자동 실행한 항목은 같은 주행에서 다시 안 함`() {
        assertNull(NudgePolicy.decide(hvacCtx(), listOf(ev(NudgeId.HVAC_ADJUST, t0 - 20 * min, NudgeOutcome.REJECTED))))
        assertNull(NudgePolicy.decide(ctx(), listOf(ev(WINTER_HEAT, t0 - 20 * min, NudgeOutcome.AUTO_EXECUTED))))
    }
}
