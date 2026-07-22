package com.radium.skylark.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.radium.skylark.bg.LogLevel
import com.radium.skylark.bg.LogLine
import com.radium.skylark.viewmodel.LogsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    modifier: Modifier = Modifier,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val lines by viewModel.lines.collectAsState()
    val minLevel by viewModel.minLevel.collectAsState()
    val listState = rememberLazyListState()

    val filtered = remember(lines, minLevel) {
        val threshold = minLevel?.ordinal ?: 0
        lines.filter { it.level.ordinal >= threshold }
    }

    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.animateScrollToItem(filtered.size - 1)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                actions = {
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空日志")
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            LevelFilterRow(
                selected = minLevel,
                onSelect = { viewModel.setMinLevel(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无日志", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filtered, key = { it.id }) { line -> LogRow(line) }
                }
            }
        }
    }
}

@Composable
private fun LevelFilterRow(
    selected: LogLevel?,
    onSelect: (LogLevel?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("全部") },
        )
        LogLevel.entries.forEach { level ->
            FilterChip(
                selected = selected == level,
                onClick = { onSelect(level) },
                label = { Text(level.name) },
            )
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val time = remember(line.timestamp) { TIME_FORMAT.format(Date(line.timestamp)) }
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Text(
            text = "$time  ${line.level.name.padEnd(5)}  [${line.tag}] ${line.message}",
            color = colorForLevel(line.level),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun colorForLevel(level: LogLevel): Color = when (level) {
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
}

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)
