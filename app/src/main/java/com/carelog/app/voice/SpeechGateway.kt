package com.carelog.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/**
 * Pleos TTS·STT (온디바이스, ko-KR)를 suspend 함수로 감싼다.
 * 한 번에 한 대화만 진행되도록 [conversation]으로 직렬화한다 (충전 확인과 넛지가 겹치지 않게).
 */
class SpeechGateway(context: Context, private val sttWaitMs: () -> Long) {
    private val tts = TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
    private val stt = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
    private val ttsReady = CompletableDeferred<Boolean>()
    private val sttReady = CompletableDeferred<Boolean>()
    private val ttsEvents = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 16)

    /** 듣는 동안만 STT 이벤트를 받는 곳. request() 전에 열어 두므로 준비 신호를 놓치지 않는다. */
    @Volatile
    private var sttSink: SendChannel<SttEvent>? = null
    private val mutex = Mutex()

    /** 마지막 대화가 끝난 시각. 대화를 연달아 붙이지 않기 위해 넛지 판단에 쓴다. */
    @Volatile
    var lastConversationEndedAt: Long? = null
        private set

    private sealed interface TtsEvent {
        data object Done : TtsEvent
        data object Stopped : TtsEvent
        data class Failed(val error: TtsError) : TtsEvent
    }

    init {
        // 리스너는 initialize 성공 후에만 붙일 수 있다 (먼저 붙이면 SDK 내부 lateinit 크래시, 에뮬레이터 확인).
        Handler(Looper.getMainLooper()).post {
            tts.initialize(object : TtsInit {
                override fun onSuccess() {
                    tts.setLocale(Locale.KOREA)
                    tts.addEventListener(ttsListener)
                    ttsReady.complete(true)
                }
                override fun onFailure(error: TtsError) { Log.w(TAG, "TTS init failed: $error"); ttsReady.complete(false) }
            })
            stt.initialize(object : SttInit {
                override fun onSuccess() {
                    stt.setLocale(Locale.KOREA)
                    stt.addListener(sttListener)
                    sttReady.complete(true)
                }
                override fun onFailure(error: SttError) { Log.w(TAG, "STT init failed: $error"); sttReady.complete(false) }
            })
        }
    }

    private val ttsListener get() = object : EventListener {
        override fun onReady() {}
        override fun onStart() {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onDone() { ttsEvents.tryEmit(TtsEvent.Done) }
        override fun onStop() { ttsEvents.tryEmit(TtsEvent.Stopped) }
        override fun onError(error: TtsError) { ttsEvents.tryEmit(TtsEvent.Failed(error)) }
    }

    private val sttListener get() = object : ResultListener {
        override fun onReady() { sttSink?.trySend(SttEvent.Ready) }
        override fun onStartedRecognition() {}
        override fun onUpdatedEpdData(start: Long, end: Long) {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onUpdated(text: String, completed: Boolean) {
            when {
                completed -> sttSink?.trySend(SttEvent.Final(text))
                text.isNotBlank() -> sttSink?.trySend(SttEvent.Partial)
            }
        }
        override fun onEndedRecognition() { sttSink?.trySend(SttEvent.Ended) }
        override fun onError(error: SttError) { sttSink?.trySend(SttEvent.Failed(error)) }
    }

    suspend fun <T> conversation(block: suspend SpeechGateway.() -> T): T = mutex.withLock {
        try {
            block()
        } finally {
            lastConversationEndedAt = System.currentTimeMillis()
        }
    }

    /** 끝까지 말했으면 true. 초기화 실패·오류·시간 초과는 false. */
    suspend fun speak(text: String): Boolean {
        if (!ready(ttsReady)) return false
        Log.i(TAG, "speak: $text")
        return coroutineScope {
            val end = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(SPEAK_BASE_TIMEOUT_MS + SPEAK_PER_CHAR_MS * text.length) {
                    ttsEvents.first()
                }
            }
            withContext(Dispatchers.Main) { tts.speak(text, true) }
            val result = end.await()
            if (result == null) withContext(Dispatchers.Main) { tts.stop() }
            if (result != TtsEvent.Done) Log.w(TAG, "speak ended with ${result ?: "timeout"}")
            result == TtsEvent.Done
        }
    }

    /**
     * 한 번 듣고 최종 인식 문장을 돌려준다. 무응답·미인식·오류·시간 초과는 null.
     * 대기 시간은 STT 준비 완료부터 잰다 (설정 3/5/7초, [awaitAnswer]).
     * 중간 인식 결과는 쓰지 않는다 — 제어 동의는 확정된 문장으로만 판단 (PRD 6.3).
     */
    suspend fun listen(responseMs: Long = sttWaitMs()): String? {
        if (!ready(sttReady)) return null
        val events = Channel<SttEvent>(Channel.UNLIMITED)
        sttSink = events
        try {
            withContext(Dispatchers.Main) { stt.request() }
            val result = awaitAnswer(events, responseMs)
            if (result == null) withContext(Dispatchers.Main) { stt.stop() }
            Log.i(TAG, "listen(${responseMs}ms): ${result ?: "timeout"}")
            return (result as? SttEvent.Final)?.text
        } finally {
            sttSink = null
        }
    }

    private suspend fun ready(init: CompletableDeferred<Boolean>) =
        withTimeoutOrNull(INIT_TIMEOUT_MS) { init.await() } == true

    private companion object {
        const val TAG = "SpeechGateway"
        const val INIT_TIMEOUT_MS = 5_000L
        const val SPEAK_BASE_TIMEOUT_MS = 5_000L
        const val SPEAK_PER_CHAR_MS = 300L
    }
}
