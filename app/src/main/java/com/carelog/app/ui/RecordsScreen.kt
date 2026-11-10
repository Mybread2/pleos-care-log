package com.carelog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.carelog.app.data.RefillRecordEntity

private enum class EditField { QUANTITY, COST }

private data class Editing(val record: RefillRecordEntity, val field: EditField)

/** 추정 기록 확인·수정 (PRD 5.2 정차 화면 액션). */
@Composable
fun RecordsScreen(vm: CareLogViewModel) {
    val records by vm.records.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("주유·충전 기록", style = MaterialTheme.typography.headlineMedium) }
        if (records.isEmpty()) item { Hint("아직 기록이 없어요. 주유·충전하면 시동을 켤 때 자동으로 추정 저장해요.") }
        items(records, key = { it.id }) { r ->
            RecordCard(
                r,
                onConfirm = { vm.confirm(r.id) },
                onNotRefill = { vm.markFalsePositive(r.id) },
                onUndo = { vm.undoFalsePositive(r.id) },
                onEdit = { field -> editing = Editing(r, field) },
            )
        }
    }

    editing?.let { e ->
        val r = e.record
        when (e.field) {
            EditField.QUANTITY -> NumberPadDialog(
                title = "실제 ${Labels.refillName(r)}량",
                unit = Labels.quantityUnit(r),
                initial = r.userQuantity?.toString() ?: "",
                allowDecimal = true,
                onDismiss = { editing = null },
                onSave = { text -> text.toFloatOrNull()?.let { vm.setQuantity(r.id, it) }; editing = null },
            )
            EditField.COST -> NumberPadDialog(
                title = "결제 금액",
                unit = "원",
                initial = r.costWon?.toString() ?: "",
                allowDecimal = false,
                onDismiss = { editing = null },
                onSave = { text -> text.toIntOrNull()?.let { vm.setCost(r.id, it) }; editing = null },
            )
        }
    }
}

@Composable
private fun RecordCard(
    r: RefillRecordEntity,
    onConfirm: () -> Unit,
    onNotRefill: () -> Unit,
    onUndo: () -> Unit,
    onEdit: (EditField) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val name = Labels.refillName(r)
    SectionCard(null, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${Labels.time(r.detectedAt)}  $name", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            when (r.confirmation) {
                Confirmation.CONFIRMED -> StatusBadge("확인됨", colors.secondary)
                Confirmation.FALSE_POSITIVE -> StatusBadge("오탐 · 집계 제외", colors.onSurfaceVariant)
                else -> StatusBadge("확인 전", colors.tertiary)
            }
        }
        InfoRow(
            if (Labels.isEv(r)) "배터리" else "연료",
            "${r.beforePercent}% → ${r.afterPercent}% (+${r.afterPercent - r.beforePercent}%p)",
            "차량 수신",
        )
        r.batteryAddedKwh?.let { InfoRow("충전량", "약 ${Labels.kwh(it)}", "환산 추정") }
        r.userQuantity?.let { InfoRow("실제 수량", "%.1f %s".format(it, Labels.quantityUnit(r)), "사용자 입력") }
        InfoRow("금액", r.costWon?.let { Labels.won(it) } ?: "미입력", r.costWon?.let { "사용자 입력" })

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (r.confirmation == Confirmation.FALSE_POSITIVE) {
                Hint("$name 기록이 아니라고 표시했어요.")
                Spacer(Modifier.width(8.dp))
                ActionButton("되돌리기", onUndo)
            } else {
                if (r.confirmation == Confirmation.UNCONFIRMED) {
                    Button(onClick = onConfirm, modifier = Modifier.height(72.dp)) {
                        Text("맞아요", style = MaterialTheme.typography.labelLarge)
                    }
                }
                ActionButton("수량 수정") { onEdit(EditField.QUANTITY) }
                ActionButton(if (r.costWon == null) "금액 입력" else "금액 수정") { onEdit(EditField.COST) }
                if (r.confirmation == Confirmation.UNCONFIRMED) ActionButton("$name 안 했어요", onNotRefill)
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(72.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
