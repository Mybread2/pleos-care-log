package com.carelog.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CareLogDao {
    @Insert
    suspend fun insertSnapshot(snapshot: SnapshotEntity): Long

    @Query("SELECT * FROM snapshot ORDER BY takenAt DESC, id DESC LIMIT 1")
    suspend fun latestSnapshot(): SnapshotEntity?

    @Query("SELECT * FROM snapshot ORDER BY takenAt DESC, id DESC LIMIT :limit")
    fun observeRecentSnapshots(limit: Int): Flow<List<SnapshotEntity>>

    /** 같은 스냅샷·유형 기록이 이미 있으면 무시 (재시작·중복 호출 대비). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(record: RefillRecordEntity): Long

    @Query("SELECT * FROM refill_record ORDER BY detectedAt DESC, id DESC")
    fun observeRecords(): Flow<List<RefillRecordEntity>>

    @Query("UPDATE refill_record SET confirmation = :confirmation WHERE id = :id")
    suspend fun updateConfirmation(id: Long, confirmation: String)

    @Query("UPDATE refill_record SET userQuantity = :quantity, confirmation = :confirmation WHERE id = :id")
    suspend fun updateQuantity(id: Long, quantity: Float, confirmation: String)

    @Insert
    suspend fun insertNudgeEvent(event: NudgeEventEntity): Long

    @Query("SELECT * FROM nudge_event WHERE at >= :from ORDER BY at")
    suspend fun nudgeEventsSince(from: Long): List<NudgeEventEntity>

    @Query("SELECT * FROM nudge_event ORDER BY at DESC, id DESC LIMIT :limit")
    fun observeNudgeEvents(limit: Int): Flow<List<NudgeEventEntity>>

    @Query("UPDATE refill_record SET costWon = :costWon WHERE id = :id")
    suspend fun updateCost(id: Long, costWon: Int?)

    @Query("SELECT MIN(odometer) AS minKm, MAX(odometer) AS maxKm FROM snapshot WHERE takenAt >= :from AND odometer IS NOT NULL")
    fun observeOdometerRange(from: Long): Flow<OdometerRange?>

    @Query("SELECT * FROM snapshot WHERE takenAt >= :from ORDER BY takenAt, id")
    fun observeSnapshotsSince(from: Long): Flow<List<SnapshotEntity>>

    @Insert
    suspend fun insertConsumableReset(reset: ConsumableResetEntity): Long

    @Query("SELECT * FROM consumable_reset ORDER BY at DESC, id DESC")
    fun observeConsumableResets(): Flow<List<ConsumableResetEntity>>
}

data class OdometerRange(val minKm: Float?, val maxKm: Float?)
