package com.carelog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 차량 화면용 숫자 입력. 시스템 키보드 대신 큰 버튼 패드를 쓴다 (터치 정확도, 에뮬레이터 IME 크래시 회피).
 * 0보다 큰 값만 저장할 수 있다.
 */
@Composable
fun NumberPadDialog(
    title: String,
    unit: String,
    initial: String,
    allowDecimal: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial.removeSuffix(".0")) }
    val valid = (text.toDoubleOrNull() ?: 0.0) > 0.0

    fun press(key: String) {
        text = when {
            key == "⌫" -> text.dropLast(1)
            key == "." && (!allowDecimal || "." in text) -> text
            text.length >= MAX_LENGTH -> text
            key == "." && text.isEmpty() -> "0."
            text == "0" && key != "." -> key
            else -> text + key
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Text(
                    if (text.isEmpty()) "— $unit" else "${formatted(text)} $unit",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf(if (allowDecimal) "." else "", "0", "⌫"),
                    ).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { key ->
                                if (key.isEmpty()) {
                                    Spacer(Modifier.size(KEY_WIDTH, KEY_HEIGHT))
                                } else {
                                    FilledTonalButton(onClick = { press(key) }, modifier = Modifier.size(KEY_WIDTH, KEY_HEIGHT)) {
                                        Text(key, style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.height(72.dp).width(160.dp)) {
                        Text("취소", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(onClick = { onSave(text) }, enabled = valid, modifier = Modifier.height(72.dp).width(160.dp)) {
                        Text("저장", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** 정수부에 천 단위 쉼표. 입력 중인 소수점은 그대로 둔다. */
private fun formatted(text: String): String {
    val int = text.substringBefore(".")
    val rest = if ("." in text) "." + text.substringAfter(".") else ""
    return (int.toLongOrNull()?.let { "%,d".format(it) } ?: int) + rest
}

private const val MAX_LENGTH = 9
private val KEY_WIDTH = 120.dp
private val KEY_HEIGHT = 80.dp
