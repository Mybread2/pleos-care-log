package com.carelog.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.carelog.app.CareLogApp
import com.carelog.app.collector.CollectorService
import com.carelog.app.data.SnapshotReason
import com.carelog.app.nudge.NudgePolicy
import com.carelog.app.vehicle.VehicleReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 백엔드 검증용 임시 화면 (V1·V5 확인). 실제 UI는 프론트 담당이 대체한다.
 */
class DebugActivity : ComponentActivity() {
    private val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CareLogApp
        CollectorService.start(this)

        val current = text("차량 값 읽는 중…")
        val records = text("")
        val snapshots = text("")
        val nudgeState = text("")
        val nudgeEvents = text("")
        val snapshotButton = button("지금 스냅샷 저장")
        val nudgeButtons = listOf(
            button("외기온 4℃ (열선)") { app.settings.outsideTempOverrideC = 4f },
            button("외기온 -2℃ (결빙)") { app.settings.outsideTempOverrideC = -2f },
            button("외기온 실제값") { app.settings.outsideTempOverrideC = null },
            button("새 주행 시작") { app.settings.startNewTrip() },
            // 에뮬레이터에선 STT에 "응"을 말할 수 없어서, 수락 후 실행 경로(설정→재조회 확인)만 따로 검증
            button("열선 1단 제어 검증") {
                lifecycleScope.launch {
                    val ok = app.vehicle.setSteeringHeat(1)
                    Toast.makeText(this@DebugActivity, "열선 1단 설정 결과: $ok", Toast.LENGTH_LONG).show()
                }
            },
            button("열선 끄기") { lifecycleScope.launch { app.vehicle.setSteeringHeat(0) } },
            button("공조 2℃ 낮춤 제어 검증") {
                lifecycleScope.launch {
                    val from = app.vehicle.readCare().hvac.driverSetpointC
                    val ok = from != null && app.vehicle.setDriverTemperature(NudgePolicy.hvacTarget(from))
                    Toast.makeText(this@DebugActivity, "공조 $from℃ → 2℃ 낮춤 결과: $ok", Toast.LENGTH_LONG).show()
                }
            },
        )

        setContentView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                addView(snapshotButton)
                addView(LinearLayout(context).apply { nudgeButtons.forEach(::addView) })
                addView(nudgeState)
                addView(nudgeEvents)
                addView(current)
                addView(records)
                addView(snapshots)
            })
        })

        snapshotButton.setOnClickListener {
            lifecycleScope.launch {
                val created = app.recorder.record(SnapshotReason.MANUAL)
                current.text = format(app.recorder.lastReading) + "\n→ 새 추정 기록 ${created}건"
            }
        }

        lifecycleScope.launch { current.text = format(app.vehicle.read()) }

        val dao = app.db.dao()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dao.observeRecords().collect { list ->
                        records.text = "■ 추정 주유·충전 기록 (${list.size})\n" + list.joinToString("\n") {
                            "${time.format(Date(it.detectedAt))}  ${it.type}  ${it.beforePercent}% → ${it.afterPercent}% " +
                                "(+${it.afterPercent - it.beforePercent}%p)  " +
                                (it.batteryAddedKwh?.let { kwh -> "배터리 +%.1fkWh  ".format(kwh) } ?: "") +
                                (it.userQuantity?.let { q -> "사용자 수량 $q  " } ?: "") +
                                it.confirmation
                        }
                    }
                }
                launch {
                    while (true) {
                        nudgeState.text = "■ 케어 넛지 판단 상태 (5초마다)\n" +
                            "외기온 대체값: ${app.settings.outsideTempOverrideC ?: "없음"}  넛지 설정: ${app.settings.nudgeEnabled}\n" +
                            (app.nudge.lastContext?.let { c ->
                                "정차=${c.parked}  EV=${c.isEv}  외기온=${c.outsideTempC}  열선=${c.steeringHeatLevel}  " +
                                    "운전석 벨트=${c.driverBeltBuckled}  창문 열림=${c.anyWindowOpen}\n공조=${c.hvac}  " +
                                    "자동 케어(열선)=${app.settings.autoHeatConsent}"
                            } ?: "아직 판단 전")
                        delay(2_000)
                    }
                }
                launch {
                    dao.observeNudgeEvents(10).collect { list ->
                        nudgeEvents.text = "■ 넛지 이력\n" + list.joinToString("\n") {
                            "${time.format(Date(it.at))}  ${it.nudge}  ${it.outcome}"
                        }
                    }
                }
                launch {
                    dao.observeRecentSnapshots(10).collect { list ->
                        snapshots.text = "■ 최근 스냅샷\n" + list.joinToString("\n") {
                            "${time.format(Date(it.takenAt))}  ${it.reason}  연료=${it.fuelPercent}%  " +
                                "SoC=${it.socPercent}%  배터리=${it.evBatteryLevel}  주행거리=${it.odometer}"
                        }
                    }
                }
            }
        }
    }

    private fun format(r: VehicleReading?): String {
        if (r == null) return "아직 읽은 값 없음"
        return """
            ■ 현재 차량 값 (${time.format(Date(r.takenAt))})
            연료 %: ${r.fuelPercent}
            배터리 SoC %: ${r.socPercent}
            배터리 잔량 (Wh): ${r.evBatteryLevel}
            배터리 용량: ${r.evBatteryCapacity}
            누적 주행거리 (km): ${r.odometer}
        """.trimIndent()
    }

    private fun button(label: String, onClick: (() -> Unit)? = null) = Button(this).apply {
        text = label
        textSize = 20f
        onClick?.let { action -> setOnClickListener { action() } }
    }

    private fun text(initial: String) = TextView(this).apply {
        text = initial
        textSize = 20f
        setPadding(0, 32, 0, 0)
    }
}
