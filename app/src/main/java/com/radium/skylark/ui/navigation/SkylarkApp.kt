package com.radium.skylark.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.radium.skylark.ui.screen.HomeScreen
import com.radium.skylark.ui.screen.LogsScreen
import com.radium.skylark.ui.screen.NodesScreen
import com.radium.skylark.ui.screen.ProfilesScreen
import com.radium.skylark.ui.screen.RouteScreen
import com.radium.skylark.ui.screen.SettingsScreen

@Composable
fun SkylarkApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == destination.route
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes),
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Home.route,
        ) {
            composable(TopDestination.Home.route) { HomeScreen(Modifier.padding(innerPadding)) }
            composable(TopDestination.Profiles.route) { ProfilesScreen(Modifier.padding(innerPadding)) }
            composable(TopDestination.Nodes.route) { NodesScreen(Modifier.padding(innerPadding)) }
            composable(TopDestination.Route.route) { RouteScreen(Modifier.padding(innerPadding)) }
            composable(TopDestination.Settings.route) { SettingsScreen(Modifier.padding(innerPadding)) }
            composable(TopDestination.Logs.route) { LogsScreen(Modifier.padding(innerPadding)) }
        }
    }
}
