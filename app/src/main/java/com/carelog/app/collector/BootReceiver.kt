package com.carelog.app.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 부팅 경로. 절전 복귀는 BOOT_COMPLETED가 오지 않을 수 있어 별도 검증 필요 (V3). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) CollectorService.start(context)
    }
}
