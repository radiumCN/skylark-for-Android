package com.radium.skylark.ui.screen

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.radium.skylark.update.UpdateChannel
import com.radium.skylark.update.UpdateResult
import com.radium.skylark.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val channel by viewModel.channel.collectAsState()
    val checking by viewModel.checking.collectAsState()
    val result by viewModel.result.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("当前版本") },
                    supportingContent = {
                        Text("${viewModel.versionName} (${viewModel.versionCode})")
                    },
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("更新通道", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = channel == UpdateChannel.STABLE,
                            onClick = { viewModel.setChannel(UpdateChannel.STABLE) },
                            label = { Text("稳定版") },
                        )
                        FilterChip(
                            selected = channel == UpdateChannel.BETA,
                            onClick = { viewModel.setChannel(UpdateChannel.BETA) },
                            label = { Text("测试版") },
                        )
                    }
                    Text(
                        when (channel) {
                            UpdateChannel.STABLE -> "只接收正式发布版本。"
                            UpdateChannel.BETA -> "接收预发布（beta）版本，也会提示更新的正式版。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { viewModel.checkForUpdate() },
                        enabled = !checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("检查中…")
                        } else {
                            Text("检查更新")
                        }
                    }
                }
            }

            result?.let { UpdateResultCard(it) }
        }
    }
}

@Composable
private fun UpdateResultCard(result: UpdateResult) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (result) {
                is UpdateResult.UpToDate -> {
                    Text("已是最新版本", style = MaterialTheme.typography.titleMedium)
                    Text("当前版本 ${result.current}", style = MaterialTheme.typography.bodySmall)
                }

                is UpdateResult.Failed -> {
                    Text("检查失败", style = MaterialTheme.typography.titleMedium)
                    Text(result.message, style = MaterialTheme.typography.bodySmall)
                }

                is UpdateResult.UpdateAvailable -> {
                    val r = result.release
                    Text(
                        "发现新版本 ${r.version}" + if (r.prerelease) "（测试版）" else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (r.notes.isNotBlank()) {
                        Text(r.notes, style = MaterialTheme.typography.bodySmall, maxLines = 8)
                    }
                    val url = r.apkUrl ?: r.htmlUrl
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, url.toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (r.apkUrl != null) "下载 APK" else "前往发布页")
                    }
                }
            }
        }
    }
}
