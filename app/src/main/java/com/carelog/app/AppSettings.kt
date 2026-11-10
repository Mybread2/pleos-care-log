package com.carelog.app

import android.content.Context
import android.os.SystemClock

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 케어 넛지 on/off (PRD: 사용자가 끌 수 있어야 함). 화면은 프론트에서. */
    var nudgeEnabled: Boolean
        get() = prefs.getBoolean(KEY_NUDGE, true)
        set(value) = prefs.edit().putBoolean(KEY_NUDGE, value).apply()

    /**
     * 스마트 자동 케어 — 핸들 열선 사전 동의 (PRD 4장·6.3, 기본 Off).
     * 범위: 정차 중 · 외기온 5℃ 이하 · EV · 열선이 꺼져 있을 때 · 1단만 · 한 주행 1번. 공조는 포함하지 않는다.
     */
    var autoHeatConsent: Boolean
        get() = prefs.getBoolean(KEY_AUTO_HEAT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_HEAT, value).apply()

    /** STT 응답 대기 시간 (PRD 4장 T_STT: 3/5/7초, 기본 5초). STT 준비 완료부터 잰다. */
    var sttWaitSeconds: Int
        get() = prefs.getInt(KEY_STT_WAIT, 5)
        set(value) = prefs.edit().putInt(KEY_STT_WAIT, value).apply()

    /**
     * 편의 넛지(열선·공조, 열선 자동 케어 포함) 최소 간격 (PRD 4장: 15분/30분/끄기, 기본 15분).
     * 0 = 편의 넛지 끄기. 어느 쪽이든 한 주행 최대 2회. 결빙·창문 안내는 영향 없음.
     */
    var convenienceGapMinutes: Int
        get() = prefs.getInt(KEY_CONVENIENCE_GAP, 15)
        set(value) = prefs.edit().putInt(KEY_CONVENIENCE_GAP, value).apply()

    /** 데모용 외기온 대체값. 에뮬레이터 Virtual Console에서 외기온을 바꿀 수 없어서 둔다. */
    var outsideTempOverrideC: Float?
        get() = if (prefs.contains(KEY_TEMP)) prefs.getFloat(KEY_TEMP, 0f) else null
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_TEMP) else putFloat(KEY_TEMP, value) }.apply()

    /** 주행 시작 = 헤드유닛 부팅 시각. 디버그로 새 주행을 시작했으면 그 시각. */
    val tripStartedAt: Long
        get() = maxOf(System.currentTimeMillis() - SystemClock.elapsedRealtime(), prefs.getLong(KEY_TRIP_RESET, 0))

    fun startNewTrip() = prefs.edit().putLong(KEY_TRIP_RESET, System.currentTimeMillis()).apply()

    private companion object {
        const val KEY_NUDGE = "nudge_enabled"
        const val KEY_AUTO_HEAT = "auto_heat_consent"
        const val KEY_STT_WAIT = "stt_wait_seconds"
        const val KEY_CONVENIENCE_GAP = "convenience_gap_minutes"
        const val KEY_TEMP = "outside_temp_override"
        const val KEY_TRIP_RESET = "trip_reset_at"
    }
}
