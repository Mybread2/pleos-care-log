package com.carelog.app.voice

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import ai.pleos.playground.stt.constant.ErrorType as SttError

/** STT 콜백을 한 줄로 모은 이벤트. */
internal sealed interface SttEvent {
    /** 말을 받을 준비가 됨 (onReady) */
    data object Ready : SttEvent
    /** 중간 인식 — 사용자가 말하기 시작함 */
    data object Partial : SttEvent
    data class Final(val text: String) : SttEvent
    data object Ended : SttEvent
    data class Failed(val error: SttError) : SttEvent
}

/**
 * STT 응답을 기다린다 (PRD 4장 T_STT: TTS 완료 후 STT 수신 준비 완료부터 잰다).
 * 1. 준비 신호를 [readyMs]까지 기다린다. 오지 않으면 그 시점부터 잰다.
 * 2. 준비 후 [responseMs] 안에 말이 시작되지 않으면 시간 초과.
 * 3. 말이 시작됐으면(중간 인식) 말하는 도중 자르지 않고 [speakingMs]까지 문장이 끝나길 기다린다.
 *
 * @return 최종 인식·종료·오류 이벤트. 시간 초과는 null.
 */
internal suspend fun awaitAnswer(
    events: ReceiveChannel<SttEvent>,
    responseMs: Long,
    readyMs: Long = 3_000L,
    speakingMs: Long = 10_000L,
): SttEvent? {
    var e = events.next(readyMs)
    if (e == null || e == SttEvent.Ready) e = events.next(responseMs) { it != SttEvent.Ready }
    if (e == SttEvent.Partial) e = events.next(speakingMs) { it != SttEvent.Partial }
    return e
}

private suspend fun ReceiveChannel<SttEvent>.next(ms: Long, accept: (SttEvent) -> Boolean = { true }): SttEvent? =
    withTimeoutOrNull(ms) {
        var e: SttEvent
        do e = receive() while (!accept(e))
        e
    }
