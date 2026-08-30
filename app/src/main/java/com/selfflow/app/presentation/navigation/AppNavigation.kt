package com.selfflow.app.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.selfflow.app.R
import com.selfflow.app.presentation.screens.alarm.AlarmScreen
import com.selfflow.app.presentation.screens.calendar.CalendarScreen
import com.selfflow.app.presentation.screens.home.HomeScreen
import com.selfflow.app.presentation.screens.notes.NotesScreen
import com.selfflow.app.presentation.screens.routine.RoutineScreen
import com.selfflow.app.presentation.screens.settings.SettingsScreen
import com.selfflow.app.presentation.screens.statistics.StatisticsScreen
import com.selfflow.app.presentation.screens.tasks.TasksScreen

private data class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, R.string.nav_home, Icons.Default.Home),
    BottomNavItem(Screen.Routine.route, R.string.nav_routine, Icons.Default.Schedule),
    BottomNavItem(Screen.Tasks.route, R.string.nav_tasks, Icons.Default.List),
    BottomNavItem(Screen.Notes.route, R.string.nav_notes, Icons.Default.Notifications),
    BottomNavItem(Screen.Calendar.route, R.string.nav_calendar, Icons.Default.DateRange)
)

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.route == item.route
                        val label = stringResource(item.labelRes)
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToAlarm = { navController.navigate(Screen.Alarm.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToStatistics = { navController.navigate(Screen.Statistics.route) }
                )
            }
            composable(Screen.Routine.route) { RoutineScreen() }
            composable(Screen.Tasks.route) { TasksScreen() }
            composable(Screen.Notes.route) { NotesScreen() }
            composable(Screen.Calendar.route) { CalendarScreen() }
            composable(Screen.Alarm.route) { AlarmScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.Statistics.route) { StatisticsScreen() }
        }
    }
}
