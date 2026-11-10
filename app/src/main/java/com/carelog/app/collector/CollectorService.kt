package com.carelog.app.collector

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.carelog.app.CareLogApp
import com.carelog.app.data.SnapshotReason
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 시작 시 스냅샷 1회 + 주기적 중간 저장 (PRD 6.4), 새 기록 음성 확인, 케어 넛지 판단. */
class CollectorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (loop?.isActive != true) {
            val app = application as CareLogApp
            val recorder = app.recorder
            loop = scope.launch {
                // 첫 스냅샷의 감지 결과를 놓치지 않도록 먼저 구독한다.
                launch(start = CoroutineStart.UNDISPATCHED) {
                    recorder.detected.collect { records -> records.forEach { safely("voice confirm") { app.refillConfirm.confirm(it) } } }
                }
                launch {
                    while (isActive) {
                        delay(NUDGE_TICK_MS)
                        safely("nudge") { app.nudge.tick() }
                    }
                }
                recordSafely(recorder, SnapshotReason.APP_START)
                while (isActive) {
                    delay(CHECKPOINT_INTERVAL_MS)
                    recordSafely(recorder, SnapshotReason.PERIODIC)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun recordSafely(recorder: SnapshotRecorder, reason: String) {
        try {
            val created = recorder.record(reason)
            Log.i(TAG, "snapshot $reason, new records=$created")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "snapshot $reason failed", e)
        }
    }

    private suspend fun safely(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "$what failed", e)
        }
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "차계부 자동 수집", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Pleos Care & Log")
            .setContentText("차량 기록을 자동으로 모으고 있어요")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private const val TAG = "CollectorService"
        private const val CHANNEL_ID = "collector"
        private const val NOTIFICATION_ID = 1

        /** 검증용 제안값 (PRD 6.4). 갱신 주기·저장 비용을 측정해 조정. */
        const val CHECKPOINT_INTERVAL_MS = 60_000L

        /** 벨트 해제 순간을 잡을 수 있을 만큼 짧게. */
        const val NUDGE_TICK_MS = 5_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CollectorService::class.java))
        }
    }
}
