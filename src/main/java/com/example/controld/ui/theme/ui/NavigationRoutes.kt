package com.example.controld.ui.theme.ui

sealed class NavigationRoutes(val route: String) {
    object Home : NavigationRoutes("home")
    object Search : NavigationRoutes("search")
    object Account : NavigationRoutes("account")
}