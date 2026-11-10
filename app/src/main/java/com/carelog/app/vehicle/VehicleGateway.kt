package com.carelog.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.content.Context
import com.carelog.app.nudge.HvacState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs

/** 값 또는 실패 사유. 둘 다 null이면 시간 초과. */
data class Field<T>(val value: T?, val error: String?) {
    override fun toString() = value?.toString() ?: "없음 (${error ?: "시간 초과"})"
}

data class VehicleReading(
    val takenAt: Long,
    val fuelPercent: Field<Int>,
    val socPercent: Field<Int>,
    val evBatteryLevel: Field<Float>,
    val evBatteryCapacity: Field<Float>,
    val odometer: Field<Float>,
) {
    val hasAnyValue: Boolean
        get() = listOf(fuelPercent, socPercent, evBatteryLevel, odometer).any { it.value != null }
}

data class CareReading(
    val isEv: Boolean,
    val steeringHeatLevel: Int?,
    val driverBeltBuckled: Boolean?,
    val anyWindowOpen: Boolean?,
    val hvac: HvacState,
)

/** Pleos Vehicle SDK의 콜백 API를 suspend 함수로 감싼다. */
class VehicleGateway(context: Context) {
    private val vehicle = Vehicle(context.applicationContext).apply { initialize() }

    /**
     * initialize() 반환이 서비스 연결 완료를 뜻하지 않으므로 (Sample App 가이드),
     * 값이 하나도 없으면 잠시 뒤 다시 읽는다.
     */
    suspend fun read(): VehicleReading {
        var reading = readOnce()
        repeat(RETRY_COUNT) {
            if (reading.hasAnyValue) return reading
            delay(RETRY_DELAY_MS)
            reading = readOnce()
        }
        return reading
    }

    private suspend fun readOnce(): VehicleReading = coroutineScope {
        val fuel = async { await<Int> { ok, fail -> vehicle.getFuel().getFuelPercent(ok, fail) } }
        val soc = async { await<Int> { ok, fail -> vehicle.getEvBattery().getEvBatteryStateOfCharge(ok, fail) } }
        val level = async { await<Float> { ok, fail -> vehicle.getEvBattery().getEvBatteryLevel(ok, fail) } }
        val capacity = async { await<Float> { ok, fail -> vehicle.getEvBattery().getEvBatteryCapacity(ok, fail) } }
        val odometer = async { await<Float> { ok, fail -> vehicle.getOdometer().getCurrentDistanceDriven(ok, fail) } }
        VehicleReading(
            takenAt = System.currentTimeMillis(),
            fuelPercent = fuel.await(),
            socPercent = soc.await(),
            evBatteryLevel = level.await(),
            evBatteryCapacity = capacity.await(),
            odometer = odometer.await(),
        )
    }

    /** 케어 넛지 판단용 상태. 운전석 = 1열 왼쪽. 창문은 하나라도 0(닫힘)보다 크면 열림. */
    suspend fun readCare(): CareReading = coroutineScope {
        val soc = async { await<Int> { ok, fail -> vehicle.getEvBattery().getEvBatteryStateOfCharge(ok, fail) } }
        val heat = async { await<Int> { ok, fail -> vehicle.getSteeringWheel().getHvacSteeringWheelHeatState(ok, fail) } }
        val belt = async {
            await<Boolean> { ok, fail -> vehicle.getSeat().getCurrentSeatBeltBuckledState(SeatArea(SeatValue.ROW_1_LEFT), ok, fail) }
        }
        val windows = WINDOWS.map { w ->
            async { await<Int> { ok, fail -> vehicle.getWindow().getCurrentWindowPosition(WindowArea(w), ok, fail) } }
        }
        val hvac = vehicle.getHVAC()
        val power = async { hvacFlag { area, ok, fail -> hvac.getHvacPowerState(area, ok, fail) } }
        val ac = async { hvacFlag { area, ok, fail -> hvac.getHvacAcState(area, ok, fail) } }
        val setpoint = async { await<Float> { ok, fail -> hvac.getHvacTemperature(DRIVER, TemperatureUnit.CELSIUS, ok, fail) } }
        val defrosters = DEFROSTERS.map { w ->
            async { await<Boolean> { ok, fail -> hvac.getHvacDefrosterState(WindowArea(w), ok, fail) } }
        }
        val positions = windows.mapNotNull { it.await().value }
        val defrost = defrosters.map { it.await().value }
        CareReading(
            isEv = soc.await().value != null,
            steeringHeatLevel = heat.await().value,
            driverBeltBuckled = belt.await().value,
            anyWindowOpen = if (positions.isEmpty()) null else positions.any { it > 0 },
            hvac = HvacState(
                powerOn = power.await(),
                acOn = ac.await(),
                defrostOn = when {
                    defrost.any { it == true } -> true
                    defrost.all { it == false } -> false
                    else -> null
                },
                driverSetpointC = setpoint.await().value,
            ),
        )
    }

    /**
     * 공조 전원·에어컨은 차량에 따라 운전석이 아니라 실내 전체 구역으로만 지원된다
     * (에뮬레이터: area 0x1 미지원, 0x75만 지원). 운전석으로 먼저 읽고 실패하면 실내 전체로 읽는다.
     */
    private suspend fun hvacFlag(get: (SeatArea, (Boolean?) -> Unit, (Exception) -> Unit) -> Unit): Boolean? =
        await<Boolean> { ok, fail -> get(DRIVER, ok, fail) }.value
            ?: await<Boolean> { ok, fail -> get(CABIN, ok, fail) }.value

    /** 운전석 설정 온도를 바꾸고, 다시 읽어 실제로 바뀌었는지 확인한다. */
    suspend fun setDriverTemperature(celsius: Float): Boolean {
        val hvac = vehicle.getHVAC()
        val set = await<Float> { ok, fail ->
            hvac.setHvacTemperature(DRIVER, celsius, TemperatureUnit.CELSIUS, { _, value -> ok(value) }, fail)
        }
        if (set.error != null) return false
        repeat(VERIFY_COUNT) {
            val now = await<Float> { ok, fail -> hvac.getHvacTemperature(DRIVER, TemperatureUnit.CELSIUS, ok, fail) }.value
            if (now != null && abs(now - celsius) < 0.1f) return true
            delay(VERIFY_DELAY_MS)
        }
        return false
    }

    /** 핸들 열선 단계를 바꾸고, 다시 읽어 실제로 바뀌었는지 확인한다. */
    suspend fun setSteeringHeat(level: Int): Boolean {
        val set = await<Int> { ok, fail -> vehicle.getSteeringWheel().setHvacSteeringWheelHeat(level, ok, fail) }
        if (set.error != null) return false
        repeat(VERIFY_COUNT) {
            val now = await<Int> { ok, fail -> vehicle.getSteeringWheel().getHvacSteeringWheelHeatState(ok, fail) }
            if (now.value == level) return true
            delay(VERIFY_DELAY_MS)
        }
        return false
    }

    private suspend fun <T : Any> await(call: (onComplete: (T?) -> Unit, onFailed: (Exception) -> Unit) -> Unit): Field<T> =
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    call(
                        { value -> if (cont.isActive) cont.resume(Field(value, if (value == null) "null 반환" else null)) },
                        { e -> if (cont.isActive) cont.resume(Field(null, e.describe())) },
                    )
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(Field(null, e.describe()))
                }
            }
        } ?: Field(null, null)

    private fun Exception.describe() = "${javaClass.simpleName}: $message"

    private companion object {
        const val CALL_TIMEOUT_MS = 3_000L
        const val RETRY_COUNT = 3
        const val RETRY_DELAY_MS = 1_000L
        const val VERIFY_COUNT = 3
        const val VERIFY_DELAY_MS = 500L
        val WINDOWS = listOf(WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT, WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT)
        val DEFROSTERS = listOf(WindowValue.FRONT_WINDSHIELD, WindowValue.REAR_WINDSHIELD)
        val DRIVER = SeatArea(SeatValue.ROW_1_LEFT)
        val CABIN = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
    }
}
