package com.carelog.app.collector

import androidx.room.withTransaction
import com.carelog.app.core.RefillDetector
import com.carelog.app.data.AppDatabase
import com.carelog.app.data.RefillRecordEntity
import com.carelog.app.data.SnapshotEntity
import com.carelog.app.vehicle.VehicleGateway
import com.carelog.app.vehicle.VehicleReading
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 스냅샷 저장 + 직전 스냅샷과 비교 + 추정 기록 저장을 한 트랜잭션으로 처리한다 (PRD 6.4).
 * 저장이 실패하면 기록도 남지 않는다.
 */
class SnapshotRecorder(
    private val db: AppDatabase,
    private val vehicle: VehicleGateway,
) {
    private val mutex = Mutex()

    /** 마지막으로 읽은 값 (디버그 화면 표시용). */
    @Volatile
    var lastReading: VehicleReading? = null
        private set

    private val _detected = MutableSharedFlow<List<RefillRecordEntity>>(extraBufferCapacity = 8)

    /** 커밋된 새 추정 기록. 음성 확인이 구독한다. */
    val detected: SharedFlow<List<RefillRecordEntity>> = _detected.asSharedFlow()

    suspend fun record(reason: String): Int {
        val reading = vehicle.read()
        lastReading = reading
        if (!reading.hasAnyValue) return 0

        val created = mutex.withLock {
            db.withTransaction {
                val dao = db.dao()
                val prev = dao.latestSnapshot()
                val snapshot = SnapshotEntity(
                    takenAt = reading.takenAt,
                    reason = reason,
                    fuelPercent = reading.fuelPercent.value,
                    socPercent = reading.socPercent.value,
                    evBatteryLevel = reading.evBatteryLevel.value,
                    odometer = reading.odometer.value,
                )
                val currId = dao.insertSnapshot(snapshot)
                if (prev == null) return@withTransaction emptyList()

                RefillDetector.detect(prev.toReading(), snapshot.toReading()).mapNotNull { candidate ->
                    val record = RefillRecordEntity(
                        detectedAt = reading.takenAt,
                        type = candidate.type.name,
                        beforePercent = candidate.beforePercent,
                        afterPercent = candidate.afterPercent,
                        beforeSnapshotId = prev.id,
                        afterSnapshotId = currId,
                        batteryAddedKwh = candidate.batteryAddedKwh,
                    )
                    dao.insertRecord(record).takeIf { it != -1L }?.let { record.copy(id = it) }
                }
            }
        }
        if (created.isNotEmpty()) _detected.emit(created)
        return created.size
    }
}
