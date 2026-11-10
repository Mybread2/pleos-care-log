package com.carelog.app.voice

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SttWaitTest {
    private val answer = SttEvent.Final("응")

    /** [script]의 (보낼 시각 ms, 이벤트)를 차례로 보낸다. 가상 시간이라 실제로 기다리지 않는다. */
    private fun TestScope.events(vararg script: Pair<Long, SttEvent>) = Channel<SttEvent>(Channel.UNLIMITED).also { ch ->
        launch { script.forEach { (at, e) -> delay(at - currentTime); ch.send(e) } }
    }

    @Test
    fun `준비 후 대기 시간 안에 답하면 그 문장`() = runTest {
        assertEquals(answer, awaitAnswer(events(0L to SttEvent.Ready, 4_000L to answer), responseMs = 5_000))
    }

    @Test
    fun `대기 시간은 질문 직후가 아니라 준비 완료부터 잰다`() = runTest {
        // 준비 2초 + 응답 4.5초 = 6.5초. 요청 시점부터 쟀다면 5초에 끊긴다.
        assertEquals(answer, awaitAnswer(events(2_000L to SttEvent.Ready, 6_500L to answer), responseMs = 5_000))
    }

    @Test
    fun `준비 후 대기 시간 동안 말이 없으면 시간 초과`() = runTest {
        assertNull(awaitAnswer(events(1_000L to SttEvent.Ready, 6_500L to answer), responseMs = 5_000))
        assertEquals(6_000L, currentTime)
    }

    @Test
    fun `대기 시간 안에 말을 시작했으면 끝까지 듣는다`() = runTest {
        val e = events(0L to SttEvent.Ready, 2_500L to SttEvent.Partial, 4_000L to SttEvent.Partial, 6_000L to answer)
        assertEquals(answer, awaitAnswer(e, responseMs = 3_000))
    }

    @Test
    fun `준비 신호가 안 오면 준비 대기 한도 뒤부터 잰다`() = runTest {
        assertEquals(answer, awaitAnswer(events(7_500L to answer), responseMs = 5_000, readyMs = 3_000))
        assertNull(awaitAnswer(events(), responseMs = 5_000, readyMs = 3_000))
    }

    @Test
    fun `인식 없이 끝나면 종료 이벤트`() = runTest {
        assertEquals(SttEvent.Ended, awaitAnswer(events(0L to SttEvent.Ready, 1_000L to SttEvent.Ended), responseMs = 5_000))
    }
}
