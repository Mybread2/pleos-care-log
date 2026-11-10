package com.carelog.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carelog.app.CareLogApp
import com.carelog.app.data.Confirmation
import com.carelog.app.data.Consumable
import com.carelog.app.data.ConsumableResetEntity
import com.carelog.app.data.ConsumableSchedule
import com.carelog.app.data.MonthlySummary
import com.carelog.app.data.TripSummary
import com.carelog.app.nudge.NudgeContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class CarView(val context: NudgeContext?, val tempOverridden: Boolean)

class CareLogViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CareLogApp
    private val dao = app.db.dao()
    private val started = SharingStarted.WhileSubscribed(5_000)
    private val monthStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val records = dao.observeRecords().stateIn(viewModelScope, started, emptyList())

    val summary = combine(dao.observeRecords(), dao.observeOdometerRange(monthStart)) { records, odometer ->
        MonthlySummary.of(records, monthStart, odometer)
    }.stateIn(viewModelScope, started, null)

    val nudgeEvents = dao.observeNudgeEvents(20).stateIn(viewModelScope, started, emptyList())

    val latestSnapshot = dao.observeRecentSnapshots(1).map { it.firstOrNull() }.stateIn(viewModelScope, started, null)

    private val dayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val today = dao.observeSnapshotsSince(dayStart).map { TripSummary.of(it) }.stateIn(viewModelScope, started, null)

    val consumables = combine(dao.observeConsumableResets(), dao.observeRecentSnapshots(1)) { resets, latest ->
        val snap = latest.firstOrNull()
        ConsumableSchedule.all(resets, snap?.odometer, snap?.let { it.socPercent != null })
    }.stateIn(viewModelScope, started, emptyList())

    /** 1-Click 초기화: 지금 시각과 현재 누적 주행거리를 다음 교체 기준점으로 저장. */
    fun resetConsumable(item: Consumable) = viewModelScope.launch {
        dao.insertConsumableReset(
            ConsumableResetEntity(item = item.name, at = System.currentTimeMillis(), odometerKm = dao.latestSnapshot()?.odometer),
        )
    }

    /** 넛지 엔진이 5초마다 읽는 차량 상태 (정차·외기온). */
    val carView = flow {
        while (true) {
            emit(CarView(app.nudge.lastContext, app.settings.outsideTempOverrideC != null))
            delay(2_000)
        }
    }.stateIn(viewModelScope, started, CarView(null, false))

    private val _nudgeEnabled = MutableStateFlow(app.settings.nudgeEnabled)
    val nudgeEnabled = _nudgeEnabled.asStateFlow()

    fun confirm(id: Long) = viewModelScope.launch { dao.updateConfirmation(id, Confirmation.CONFIRMED) }

    fun markFalsePositive(id: Long) = viewModelScope.launch { dao.updateConfirmation(id, Confirmation.FALSE_POSITIVE) }

    fun undoFalsePositive(id: Long) = viewModelScope.launch { dao.updateConfirmation(id, Confirmation.UNCONFIRMED) }

    /** 수량을 직접 입력하면 그 수량을 확인한 것으로 본다. */
    fun setQuantity(id: Long, quantity: Float) = viewModelScope.launch { dao.updateQuantity(id, quantity, Confirmation.CONFIRMED) }

    /** 금액 입력은 수량 확인 상태를 바꾸지 않는다 (PRD 6.3). */
    fun setCost(id: Long, won: Int?) = viewModelScope.launch { dao.updateCost(id, won) }

    fun setNudgeEnabled(enabled: Boolean) {
        app.settings.nudgeEnabled = enabled
        _nudgeEnabled.value = enabled
    }

    private val _autoHeatConsent = MutableStateFlow(app.settings.autoHeatConsent)
    val autoHeatConsent = _autoHeatConsent.asStateFlow()

    fun setAutoHeatConsent(consent: Boolean) {
        app.settings.autoHeatConsent = consent
        _autoHeatConsent.value = consent
    }

    private val _sttWaitSeconds = MutableStateFlow(app.settings.sttWaitSeconds)
    val sttWaitSeconds = _sttWaitSeconds.asStateFlow()

    fun setSttWaitSeconds(seconds: Int) {
        app.settings.sttWaitSeconds = seconds
        _sttWaitSeconds.value = seconds
    }

    private val _convenienceGapMinutes = MutableStateFlow(app.settings.convenienceGapMinutes)
    val convenienceGapMinutes = _convenienceGapMinutes.asStateFlow()

    fun setConvenienceGapMinutes(minutes: Int) {
        app.settings.convenienceGapMinutes = minutes
        _convenienceGapMinutes.value = minutes
    }
}
