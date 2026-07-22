package com.radium.skylark.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.radium.skylark.ui.theme.SkylarkTheme

/**
 * M0 阶段的占位页面：统一的标题栏 + 占位说明。
 * 后续里程碑用实际内容替换各页面 body。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScaffold(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(title) }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, textAlign = TextAlign.Center)
            Text(text = description, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun RouteScreen(modifier: Modifier = Modifier) =
    PlaceholderScaffold("路由", "分流规则（占位）：规则/全局/直连、分应用代理", modifier)

@Preview(showBackground = true)
@Composable
private fun RouteScreenPreview() {
    SkylarkTheme { RouteScreen() }
}
