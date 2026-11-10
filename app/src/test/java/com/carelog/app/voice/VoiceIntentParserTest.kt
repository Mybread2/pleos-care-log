package com.carelog.app.voice

import com.carelog.app.voice.VoiceIntent.Affirm
import com.carelog.app.voice.VoiceIntent.Amount
import com.carelog.app.voice.VoiceIntent.Cancel
import com.carelog.app.voice.VoiceIntent.Deny
import com.carelog.app.voice.VoiceIntent.NotRefill
import com.carelog.app.voice.VoiceIntent.Unknown
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceIntentParserTest {
    private fun p(s: String?) = VoiceIntentParser.parse(s)

    @Test
    fun `수락`() {
        listOf("응", "네", "어", "맞아", "네 맞아요", "그래", "좋아", "켜줘", "응 켜줘").forEach { assertEquals(it, Affirm, p(it)) }
    }

    @Test
    fun `거절`() {
        listOf("아니", "아니요", "싫어", "괜찮아", "됐어", "안 켜도 돼").forEach { assertEquals(it, Deny, p(it)) }
    }

    @Test
    fun `오탐`() {
        listOf("충전 안 했어", "주유한 게 아니야", "나 안 넣었어").forEach { assertEquals(it, NotRefill, p(it)) }
    }

    @Test
    fun `수량 교정`() {
        assertEquals(Amount(34f), p("나 34L 넣었어"))
        assertEquals(Amount(40.5f), p("아니 40.5야"))
    }

    @Test
    fun `취소는 오탐이 아님`() {
        assertEquals(Cancel, p("취소"))
        assertEquals(Cancel, p("그만해"))
    }

    @Test
    fun `무응답과 미인식은 Unknown`() {
        assertEquals(Unknown, p(null))
        assertEquals(Unknown, p("  "))
        assertEquals(Unknown, p("오늘 날씨 어때"))
    }

    @Test
    fun `어가 들어간 다른 말은 수락으로 보지 않음`() {
        assertEquals(Unknown, p("어디야"))
    }
}
