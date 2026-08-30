package com.selfflow.app.presentation.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Routine : Screen("routine")
    data object Tasks : Screen("tasks")
    data object Notes : Screen("notes")
    data object Calendar : Screen("calendar")
    data object Alarm : Screen("alarm")
    data object Settings : Screen("settings")
    data object Statistics : Screen("statistics")
}
