package com.radium.skylark.ui.screen

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.radium.skylark.bg.ConnectionState
import com.radium.skylark.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.connectionState.collectAsState()
    val profile by viewModel.selectedProfile.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connect()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Skylark") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                ConnectButton(
                    state = state,
                    onClick = {
                        if (state.isActive) {
                            viewModel.disconnect()
                        } else {
                            val prepareIntent = VpnService.prepare(context)
                            if (prepareIntent != null) {
                                vpnPermissionLauncher.launch(prepareIntent)
                            } else {
                                viewModel.connect()
                            }
                        }
                    },
                )
            }

            SelectedProfileCard(
                name = profile?.name,
                nodeCount = profile?.nodeCount ?: 0,
            )
        }
    }
}

@Composable
private fun ConnectButton(
    state: ConnectionState,
    onClick: () -> Unit,
) {
    val (statusText, active) = when (state) {
        ConnectionState.Disconnected -> "未连接" to false
        ConnectionState.Connecting -> "连接中…" to true
        is ConnectionState.Connected -> "已连接" to true
        ConnectionState.Stopping -> "断开中…" to true
        is ConnectionState.Error -> "错误：${state.message}" to false
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(140.dp),
            shape = CircleShape,
            colors = if (active) {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                )
            } else {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            Icon(
                Icons.Filled.Power,
                contentDescription = if (active) "断开" else "连接",
                modifier = Modifier.size(56.dp),
            )
        }
        Text(text = statusText, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (state is ConnectionState.Connected) {
            ElapsedTimer(since = state.since)
        }
    }
}

@Composable
private fun ElapsedTimer(since: Long) {
    var now by remember(since) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = ((now - since).coerceAtLeast(0)) / 1000
    val h = elapsed / 3600
    val m = (elapsed % 3600) / 60
    val s = elapsed % 60
    Text(
        text = "%02d:%02d:%02d".format(h, m, s),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SelectedProfileCard(name: String?, nodeCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(name ?: "未选择订阅") },
            supportingContent = {
                Text(if (name != null) "$nodeCount 个节点 · 使用 Auto 自动选择" else "在「订阅」页添加并选择一个订阅")
            },
        )
    }
}
