package com.carelog.app

import android.app.Application
import com.carelog.app.collector.SnapshotRecorder
import com.carelog.app.data.AppDatabase
import com.carelog.app.nudge.NudgeEngine
import com.carelog.app.vehicle.CarState
import com.carelog.app.vehicle.VehicleGateway
import com.carelog.app.voice.RefillVoiceConfirm
import com.carelog.app.voice.SpeechGateway

/** 서비스와 화면이 같은 DB·차량 연결을 쓰도록 프로세스 단위로 하나만 만든다. */
class CareLogApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val vehicle: VehicleGateway by lazy { VehicleGateway(this) }
    val recorder: SnapshotRecorder by lazy { SnapshotRecorder(db, vehicle) }
    val settings: AppSettings by lazy { AppSettings(this) }
    val car: CarState by lazy { CarState(this) }
    val speech: SpeechGateway by lazy { SpeechGateway(this) { settings.sttWaitSeconds * 1_000L } }
    val refillConfirm: RefillVoiceConfirm by lazy { RefillVoiceConfirm(db.dao(), speech, car) }
    val nudge: NudgeEngine by lazy { NudgeEngine(db.dao(), vehicle, car, speech, settings) }
}
