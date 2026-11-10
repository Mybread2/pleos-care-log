package com.carelog.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SnapshotEntity::class, RefillRecordEntity::class, NudgeEventEntity::class, ConsumableResetEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): CareLogDao

    companion object {
        // TODO: 첫 배포 전 실제 마이그레이션으로 교체. 개발 중에는 스키마 변경 시 로컬 데이터 초기화.
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "carelog.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
