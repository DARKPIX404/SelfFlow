package com.selfflow.app.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.selfflow.app.R
import com.selfflow.app.presentation.screens.alarm.AlarmScreen
import com.selfflow.app.presentation.screens.calendar.CalendarScreen
import com.selfflow.app.presentation.screens.home.HomeScreen
import com.selfflow.app.presentation.screens.notes.NotesScreen
import com.selfflow.app.presentation.screens.onboarding.OnboardingScreen
import com.selfflow.app.presentation.screens.routine.RoutineScreen
import com.selfflow.app.presentation.screens.settings.SettingsScreen
import com.selfflow.app.presentation.screens.statistics.StatisticsScreen
import com.selfflow.app.presentation.screens.tasks.TasksScreen

private data class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

private const val TRANSITION_DURATION = 350

private val bottomNavRoutes by lazy { bottomNavItems.map { it.route }.toSet() }

private val AnimatedContentTransitionScope<NavBackStackEntry>.isBottomNavTransition: Boolean
    get() = initialState.destination.route in bottomNavRoutes &&
        targetState.destination.route in bottomNavRoutes

private fun crossfadeIn() = fadeIn(tween(TRANSITION_DURATION)) +
    scaleIn(initialScale = 0.96f, animationSpec = tween(TRANSITION_DURATION))

private fun crossfadeOut() = fadeOut(tween(TRANSITION_DURATION)) +
    scaleOut(targetScale = 0.96f, animationSpec = tween(TRANSITION_DURATION))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideIn(): EnterTransition =
    if (isBottomNavTransition) {
        crossfadeIn()
    } else {
        fadeIn(tween(TRANSITION_DURATION)) +
            scaleIn(initialScale = 0.95f, animationSpec = tween(TRANSITION_DURATION)) +
            slideInHorizontally { it / 5 }
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOut(): ExitTransition =
    if (isBottomNavTransition) {
        crossfadeOut()
    } else {
        fadeOut(tween(TRANSITION_DURATION)) +
            scaleOut(targetScale = 0.95f, animationSpec = tween(TRANSITION_DURATION)) +
            slideOutHorizontally { -it / 5 }
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popSlideIn(): EnterTransition =
    if (isBottomNavTransition) {
        crossfadeIn()
    } else {
        fadeIn(tween(TRANSITION_DURATION)) +
            scaleIn(initialScale = 0.95f, animationSpec = tween(TRANSITION_DURATION)) +
            slideInHorizontally { -it / 5 }
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popSlideOut(): ExitTransition =
    if (isBottomNavTransition) {
        crossfadeOut()
    } else {
        fadeOut(tween(TRANSITION_DURATION)) +
            scaleOut(targetScale = 0.95f, animationSpec = tween(TRANSITION_DURATION)) +
            slideOutHorizontally { it / 5 }
    }

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, R.string.nav_home, Icons.Default.Home),
    BottomNavItem(Screen.Routine.route, R.string.nav_routine, Icons.Default.Schedule),
    BottomNavItem(Screen.Tasks.route, R.string.nav_tasks, Icons.AutoMirrored.Filled.List),
    BottomNavItem(Screen.Notes.route, R.string.nav_notes, Icons.Default.CalendarMonth),
    BottomNavItem(Screen.Calendar.route, R.string.nav_calendar, Icons.Default.DateRange)
)

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                TelegramBottomBar(
                    currentRoute = currentDestination?.route,
                    onNavigate = { route ->
                        navController.navigate(route) {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = Screen.Onboarding.route,
                enterTransition = {
                    fadeIn(tween(400)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(400))
                },
                exitTransition = {
                    fadeOut(tween(300)) +
                        scaleOut(targetScale = 0.98f, animationSpec = tween(300))
                }
            ) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = Screen.Home.route,
                enterTransition = { slideIn() },
                exitTransition = { slideOut() },
                popEnterTransition = { popSlideIn() },
                popExitTransition = { popSlideOut() }
            ) {
                HomeScreen(
                    onNavigateToAlarm = { navController.navigate(Screen.Alarm.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToStatistics = { navController.navigate(Screen.Statistics.route) }
                )
            }
            composable(
                route = Screen.Routine.route,
                enterTransition = { slideIn() },
                exitTransition = { slideOut() },
                popEnterTransition = { popSlideIn() },
                popExitTransition = { popSlideOut() }
            ) { RoutineScreen() }
            composable(
                route = Screen.Tasks.route,
                enterTransition = { slideIn() },
                exitTransition = { slideOut() },
                popEnterTransition = { popSlideIn() },
                popExitTransition = { popSlideOut() }
            ) { TasksScreen() }
            composable(
                route = Screen.Notes.route,
                enterTransition = { slideIn() },
                exitTransition = { slideOut() },
                popEnterTransition = { popSlideIn() },
                popExitTransition = { popSlideOut() }
            ) { NotesScreen() }
            composable(
                route = Screen.Calendar.route,
                enterTransition = { slideIn() },
                exitTransition = { slideOut() },
                popEnterTransition = { popSlideIn() },
                popExitTransition = { popSlideOut() }
            ) { CalendarScreen() }
            composable(
                route = Screen.Alarm.route,
                enterTransition = { slideIn() },
                exitTransition = { slideOut() },
                popEnterTransition = { popSlideIn() },
                popExitTransition = { popSlideOut() }
            ) { AlarmScreen() }
            composable(
                route = Screen.Settings.route,
                enterTransition = { slideIn() },
                exitTransition = { slideOut() },
                popEnterTransition = { popSlideIn() },
                popExitTransition = { popSlideOut() }
            ) { SettingsScreen() }
            composable(
                route = Screen.Statistics.route,
                enterTransition = { slideIn() },
                exitTransition = { slideOut() },
                popEnterTransition = { popSlideIn() },
                popExitTransition = { popSlideOut() }
            ) { StatisticsScreen() }
        }
    }
}

@Composable
private fun TelegramBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                val label = stringResource(item.labelRes)
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 1f,
                    label = "nav_item_scale"
                )

                BottomNavItem(
                    icon = item.icon,
                    label = label,
                    selected = selected,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigate(item.route)
                    },
                    modifier = Modifier.scale(scale)
                )
            }
        }
    }
}

@Composable
private fun RowScope.BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Box(
        modifier = modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 32.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
