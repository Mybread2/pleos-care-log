package com.carelog.app.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.property.CarPropertyManager
import android.content.Context

/** Pleos SDK에 없는 값(주차 여부·외기온)을 AAOS Car API로 읽는다. 판정 불가는 null. */
class CarState(context: Context) {
    private val car: Car? = runCatching { Car.createCar(context.applicationContext) }.getOrNull()
    private val ux = runCatching { car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager }.getOrNull()
    private val props = runCatching { car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager }.getOrNull()

    /** 주의 분산 최적화가 필요 없는 상태 = 정차. 상태를 모르면 플랫폼이 전면 제한을 주므로 false가 된다. */
    fun isParked(): Boolean? = runCatching {
        ux?.currentCarUxRestrictions?.isRequiresDistractionOptimization?.not()
    }.getOrNull()

    fun outsideTempC(): Float? = runCatching {
        props?.getFloatProperty(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, 0)
    }.getOrNull()
}
