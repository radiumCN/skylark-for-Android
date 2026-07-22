package com.radium.skylark.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.radium.skylark.R

/**
 * 应用顶层目的地（对应底部导航的 6 个主页面）。
 */
enum class TopDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home("home", R.string.nav_home, Icons.Filled.Home),
    Profiles("profiles", R.string.nav_profiles, Icons.Filled.Subscriptions),
    Nodes("nodes", R.string.nav_nodes, Icons.Filled.Dns),
    Route("route", R.string.nav_route, Icons.Filled.Route),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
    Logs("logs", R.string.nav_logs, Icons.Filled.Terminal),
}
