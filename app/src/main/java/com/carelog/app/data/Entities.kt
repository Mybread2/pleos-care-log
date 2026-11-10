package com.carelog.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.carelog.app.core.EnergyReading

/** 차량에서 받은 원시값 그대로. 받지 못한 값은 null. */
@Entity(tableName = "snapshot")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenAt: Long,
    val reason: String,
    val fuelPercent: Int?,
    val socPercent: Int?,
    /** EvBattery.getEvBatteryLevel 원시값 (Wh, 에뮬레이터 확인). deprecated API라 교체 예정. */
    val evBatteryLevel: Float?,
    /** km (에뮬레이터 확인) */
    val odometer: Float?,
) {
    fun toReading() = EnergyReading(takenAt, fuelPercent, socPercent, odometer, evBatteryLevel)
}

object SnapshotReason {
    const val APP_START = "APP_START"
    const val PERIODIC = "PERIODIC"
    const val MANUAL = "MANUAL"
}

/** 잔량 차분으로 추정한 주유·충전 기록. 사용자 확인 여부는 별도 필드로 관리 (PRD 6.3). */
@Entity(
    tableName = "refill_record",
    indices = [Index(value = ["afterSnapshotId", "type"], unique = true)],
)
data class RefillRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val detectedAt: Long,
    val type: String,
    val beforePercent: Int,
    val afterPercent: Int,
    val beforeSnapshotId: Long,
    val afterSnapshotId: Long,
    /** EV 배터리 순증량 (kWh). 충전기 과금량 아님. */
    val batteryAddedKwh: Float? = null,
    /** 사용자가 말하거나 입력한 실제 수량 (EV=kWh, 연료=L). 있으면 추정값 대신 쓴다. */
    val userQuantity: Float? = null,
    /** 사용자가 입력한 결제 금액 (원). 없으면 null — 0원으로 합산하지 않음. */
    val costWon: Int? = null,
    /** 수량 확인 여부. 금액 입력은 이 값을 바꾸지 않음 (PRD 6.3). */
    val confirmation: String = Confirmation.UNCONFIRMED,
)

/** 케어 넛지 발화·결과 이력 (PRD 기능 5: 제안·수락·실행 이력). */
@Entity(tableName = "nudge_event")
data class NudgeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nudge: String,
    val at: Long,
    val outcome: String,
)

/** 소모품 교체 기록. 항목별 최신 기록이 다음 교체 계산의 기준점 (PRD 기능 3: 1-Click 초기화). */
@Entity(tableName = "consumable_reset")
data class ConsumableResetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val item: String,
    val at: Long,
    /** 교체 시점 누적 주행거리 (km). 받지 못했으면 null → 거리 기준은 계산하지 않는다. */
    val odometerKm: Float?,
)

object Confirmation {
    const val UNCONFIRMED = "UNCONFIRMED"
    const val CONFIRMED = "CONFIRMED"
    const val FALSE_POSITIVE = "FALSE_POSITIVE"
}
