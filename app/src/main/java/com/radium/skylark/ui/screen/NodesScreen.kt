package com.radium.skylark.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.radium.skylark.viewmodel.NodesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(
    modifier: Modifier = Modifier,
    viewModel: NodesViewModel = hiltViewModel(),
) {
    val nodes by viewModel.nodes.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("节点 (${nodes.size})") }) },
    ) { inner ->
        if (nodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无节点，请先在「配置组」添加订阅。", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(nodes) { node ->
                    Card {
                        ListItem(
                            headlineContent = {
                                Text(node.tag, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text("${node.protocol} · ${node.server}:${node.serverPort}")
                            },
                            trailingContent = { Text(node.profileName, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }
    }
}
