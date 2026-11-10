package com.carelog.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 사용자 설정 (PRD 4장). 구현된 항목만 노출한다. */
@Composable
fun SettingsScreen(vm: CareLogViewModel) {
    val nudgeEnabled by vm.nudgeEnabled.collectAsStateWithLifecycle()
    val autoHeat by vm.autoHeatConsent.collectAsStateWithLifecycle()
    val sttWait by vm.sttWaitSeconds.collectAsStateWithLifecycle()
    val convenienceGap by vm.convenienceGapMinutes.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)
        SectionCard(null, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("케어 넛지", style = MaterialTheme.typography.titleMedium)
                    Hint("정차 중 필요한 순간에 차량 기능을 음성으로 제안해요. 제어는 '응'처럼 분명히 수락했을 때만 해요.")
                }
                Switch(
                    checked = nudgeEnabled,
                    onCheckedChange = vm::setNudgeEnabled,
                    modifier = Modifier.padding(start = 24.dp).scale(1.5f),
                )
            }
        }
        SectionCard(null, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("스마트 자동 케어 · 핸들 열선", style = MaterialTheme.typography.titleMedium)
                    Hint("켜 두면 묻지 않고 켠 뒤 알려 드려요. 정차 중 · 외기온 5℃ 이하 · 열선이 꺼져 있을 때 · 1단만 · 한 주행에 1번.")
                    Hint("공조 온도는 포함하지 않아요. 직접 끄거나 거절하면 그 주행 동안 다시 켜지 않아요. 여기서 언제든 해제할 수 있어요.")
                }
                Switch(
                    checked = autoHeat,
                    onCheckedChange = vm::setAutoHeatConsent,
                    enabled = nudgeEnabled,
                    modifier = Modifier.padding(start = 24.dp).scale(1.5f),
                )
            }
        }
        SectionCard(null, Modifier.fillMaxWidth()) {
            Text("편의 제안 간격", style = MaterialTheme.typography.titleMedium)
            Hint("핸들 열선·공조 온도 제안 사이의 최소 간격이에요. 어느 쪽이든 한 주행에 최대 2번이에요.")
            Hint("끄면 열선·공조 제안과 열선 자동 케어가 모두 멈춰요. 결빙·창문 안내는 계속해요.")
            Choices(listOf(15 to "15분", 30 to "30분", 0 to "끄기"), convenienceGap, enabled = nudgeEnabled, onSelect = vm::setConvenienceGapMinutes)
        }
        SectionCard(null, Modifier.fillMaxWidth()) {
            Text("음성 응답 대기", style = MaterialTheme.typography.titleMedium)
            Hint("질문이 끝나고 마이크가 준비된 뒤부터 재요. 그동안 대답이 없으면 기록은 추정으로 두고, 차량 제어는 하지 않아요.")
            Choices(listOf(3 to "3초", 5 to "5초", 7 to "7초"), sttWait, onSelect = vm::setSttWaitSeconds)
        }
        SectionCard(null, Modifier.fillMaxWidth()) {
            Text("주행 중 음성 안내", style = MaterialTheme.typography.titleMedium)
            Hint("주행 중 음성 정책을 확인하기 전이라 꺼져 있어요. 기록 확인 질문과 케어 넛지는 정차 중에만 해요.")
        }
        OutlinedButton(
            onClick = { context.startActivity(Intent(context, DebugActivity::class.java)) },
            modifier = Modifier.height(72.dp),
        ) {
            Text("개발자 화면 (시연·검증용)", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 하나만 고르는 큰 버튼 줄. 고른 항목은 채운 버튼으로 보인다. */
@Composable
private fun Choices(options: List<Pair<Int, String>>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        options.forEach { (value, label) ->
            val modifier = Modifier.height(72.dp).widthIn(min = 160.dp).semantics { this.selected = value == selected }
            if (value == selected) {
                Button(onClick = {}, enabled = enabled, modifier = modifier) { Text(label, style = MaterialTheme.typography.labelLarge) }
            } else {
                OutlinedButton(onClick = { onSelect(value) }, enabled = enabled, modifier = modifier) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
