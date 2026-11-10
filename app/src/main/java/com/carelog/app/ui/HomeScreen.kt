package com.carelog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carelog.app.data.Confirmation
import com.carelog.app.data.Consumable
import com.carelog.app.data.ConsumableStatus
import com.carelog.app.data.MonthlySummary
import com.carelog.app.data.NudgeEventEntity
import com.carelog.app.data.RefillRecordEntity
import com.carelog.app.data.SnapshotEntity
import com.carelog.app.data.TripSummary
import kotlin.math.roundToInt

/** 정차 대시보드 (PRD 5.1, 기능 4). */
@Composable
fun HomeScreen(vm: CareLogViewModel, onOpenRecords: () -> Unit) {
    val records by vm.records.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val snapshot by vm.latestSnapshot.collectAsStateWithLifecycle()
    val car by vm.carView.collectAsStateWithLifecycle()
    val events by vm.nudgeEvents.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val consumables by vm.consumables.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<Consumable?>(null) }
    val pending = records.filter { it.confirmation == Confirmation.UNCONFIRMED }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Pleos Care & Log", style = MaterialTheme.typography.headlineMedium)
        if (pending.isNotEmpty()) PendingCard(pending, onOpenRecords)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SectionCard("오늘 주행", Modifier.weight(1f)) { TodayTrip(today, isEv = snapshot?.let { it.socPercent != null }) }
            SectionCard("이번 달", Modifier.weight(1f)) { MonthSummary(summary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SectionCard("차량 상태", Modifier.weight(1f)) { CarStatus(snapshot, car) }
            SectionCard("소모품", Modifier.weight(1f)) { ConsumableList(consumables) { confirming = it } }
        }
        SectionCard("케어 넛지 이력") { NudgeHistory(events.take(5)) }
    }

    confirming?.let { item ->
        val km = snapshot?.odometer
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("${item.label} 교체 기록", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    if (km != null) "오늘 교체한 것으로 기록하고, 현재 누적 주행거리 ${Labels.km(km)}를 다음 교체 기준으로 잡을게요."
                    else "오늘 교체한 것으로 기록할게요. 주행거리를 받지 못해 기간으로만 다음 교체 시기를 계산해요.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                Button(onClick = { vm.resetConsumable(item); confirming = null }, modifier = Modifier.height(72.dp)) {
                    Text("기록", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirming = null }, modifier = Modifier.height(72.dp)) {
                    Text("취소", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

/** 오늘 주행 요약 (PRD 5.3). 시동 Off 때 못 보여준 요약도 다음 실행에 여기서 본다. */
@Composable
private fun TodayTrip(t: TripSummary?, isEv: Boolean?) {
    if (t == null) return Hint("불러오는 중…")
    if (t.operatingMinutes == null) return Hint("오늘 기록이 아직 부족해요. 시동이 켜져 있으면 1분마다 저장해요.")
    InfoRow("주행거리", Labels.km(t.distanceKm), t.distanceKm?.let { "차량 수신" })
    InfoRow("운행 시간", Labels.minutes(t.operatingMinutes))
    InfoRow(
        if (isEv == false) "평균 연비" else "평균 전비",
        t.kmPerKwh?.let { "%.1f km/kWh".format(it) } ?: "—",
        t.kmPerKwh?.let { "환산 추정" },
    )
    t.efficiencyNote?.let { Hint(it) }
    if (t.gapCount > 0) Hint("기록이 끊긴 구간 ${t.gapCount}곳은 운행 시간에서 빠져 있어요.")
}

/** 소모품 타임라인 + 1-Click 초기화 (기능 3·4). 기준점이 없으면 남은 거리를 단정하지 않는다. */
@Composable
private fun ConsumableList(items: List<ConsumableStatus>, onReset: (Consumable) -> Unit) {
    val now = System.currentTimeMillis()
    items.forEach { s ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.item.label, style = MaterialTheme.typography.titleMedium)
                    if (s.isDue(now)) {
                        Spacer(Modifier.width(12.dp))
                        StatusBadge("교체 시기", MaterialTheme.colorScheme.tertiary)
                    }
                }
                Hint(Labels.consumable(s))
            }
            OutlinedButton(onClick = { onReset(s.item) }, modifier = Modifier.height(72.dp)) {
                Text("교체함", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    Hint("주기는 일반 권장 예시예요. 차량 설명서 기준과 다를 수 있어요.")
}

@Composable
private fun PendingCard(pending: List<RefillRecordEntity>, onOpen: () -> Unit) {
    val kind = when {
        pending.all { Labels.isEv(it) } -> "충전"
        pending.none { Labels.isEv(it) } -> "주유"
        else -> "주유·충전"
    }
    SectionCard(null, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("추정 저장한 $kind 기록 ${pending.size}건이 있습니다.", style = MaterialTheme.typography.titleMedium)
                Hint("수량이 맞는지 확인하고, 결제 금액을 입력할 수 있어요.")
            }
            Button(onClick = onOpen, modifier = Modifier.height(72.dp)) {
                Text("지금 확인", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MonthSummary(s: MonthlySummary?) {
    if (s == null) return Hint("불러오는 중…")
    InfoRow("주유·충전", "${s.refillCount}회", "자동 기록")
    // 값이 없으면 출처 레이블도 붙이지 않는다
    InfoRow("배터리 충전량", Labels.kwh(s.batteryAddedKwh), s.batteryAddedKwh?.let { "환산 추정" })
    InfoRow("입력한 지출", Labels.won(s.totalCostWon), s.totalCostWon?.let { "사용자 입력" })
    if (s.missingCostCount > 0) Hint("금액 미입력 ${s.missingCostCount}건은 합계에서 빠져 있어요.")
    InfoRow("주행거리", Labels.km(s.distanceKm), s.distanceKm?.let { "차량 수신" })
    Hint("충전량은 배터리에 들어간 양이라 충전기 과금량과 다를 수 있어요. 오탐 기록은 집계에서 빼요.")
}

@Composable
private fun CarStatus(snapshot: SnapshotEntity?, car: CarView) {
    val ctx = car.context
    InfoRow("상태", when (ctx?.parked) { true -> "정차 중"; false -> "주행 중"; null -> "확인 중" })
    snapshot?.socPercent?.let { InfoRow("배터리", "$it%", "차량 수신") }
    snapshot?.fuelPercent?.let { InfoRow("연료", "$it%", "차량 수신") }
    InfoRow("누적 주행거리", Labels.km(snapshot?.odometer), "차량 수신")
    InfoRow(
        "외기온",
        ctx?.outsideTempC?.let { "${it.roundToInt()}℃" } ?: "—",
        if (car.tempOverridden) "시연용 대체값" else "차량 수신",
    )
    snapshot?.let { Hint("마지막 저장 ${Labels.time(it.takenAt)}") }
}

@Composable
private fun NudgeHistory(events: List<NudgeEventEntity>) {
    if (events.isEmpty()) return Hint("아직 케어 넛지 이력이 없어요. 정차 중 필요한 순간에 음성으로 제안해요.")
    events.forEach { e ->
        Row {
            Text(Labels.time(e.at), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(0.25f))
            Text(Labels.nudge(e.nudge), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(0.45f))
            Text(Labels.outcome(e.outcome), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
