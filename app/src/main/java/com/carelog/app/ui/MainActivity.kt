package com.carelog.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carelog.app.collector.CollectorService
import com.carelog.app.ui.theme.CareLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CollectorService.start(this)
        setContent { CareLogTheme { CareLogRoot() } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Filled.Home),
    RECORDS("기록", Icons.AutoMirrored.Filled.List),
    SETTINGS("설정", Icons.Filled.Settings),
}

@Composable
private fun CareLogRoot(vm: CareLogViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Spacer(Modifier.height(24.dp))
                Tab.entries.forEach { t ->
                    NavigationRailItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label, modifier = Modifier.size(36.dp)) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 40.dp, vertical = 32.dp)) {
                when (tab) {
                    Tab.HOME -> HomeScreen(vm, onOpenRecords = { tab = Tab.RECORDS })
                    Tab.RECORDS -> RecordsScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }
}
