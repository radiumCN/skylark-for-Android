package com.radium.skylark.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.radium.skylark.data.db.ProfileEntity
import com.radium.skylark.viewmodel.ProfilesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedId by viewModel.selectedProfileId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("配置组") },
                actions = {
                    if (uiState.busy) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加订阅")
            }
        },
    ) { inner ->
        if (profiles.isEmpty()) {
            EmptyHint(
                text = "还没有订阅。点击右下角 + 添加订阅链接或粘贴配置。",
                modifier = Modifier.fillMaxSize().padding(inner),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val isSelected = profile.id == selectedId ||
                        (selectedId == null && profile.id == profiles.first().id)
                    ProfileCard(
                        profile = profile,
                        selected = isSelected,
                        onSelect = { viewModel.select(profile) },
                        onRefresh = { viewModel.refresh(profile) },
                        onDelete = { viewModel.delete(profile) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, content ->
                showAddDialog = false
                if (content.trim().startsWith("http")) {
                    viewModel.addFromUrl(name, content.trim())
                } else {
                    viewModel.addFromText(name, content)
                }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ProfileEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onSelect,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            headlineContent = {
                Text(
                    (if (selected) "✓ " else "") + profile.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text("${profile.type} · ${profile.nodeCount} 个节点")
            },
            trailingContent = {
                androidx.compose.foundation.layout.Row {
                    if (profile.type == "url") {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
            },
        )
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, content: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加订阅") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（可选）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("订阅链接或粘贴配置内容") },
                    minLines = 3,
                )
                Text(
                    "以 http 开头视为订阅链接，否则按粘贴的文本/Clash YAML/分享链接解析。",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, content) },
                enabled = content.isNotBlank(),
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
