package com.privacyshield.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.privacyshield.ui.camera.CameraScreen
import com.privacyshield.ui.chat.ChatScreen
import com.privacyshield.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Camera   : Screen("camera")
    object Chat     : Screen("chat")
    object Settings : Screen("settings")
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Camera.route) {
        composable(Screen.Camera.route) {
            CameraScreen(onNavigateToChat = { navController.navigate(Screen.Chat.route) })
        }
        composable(Screen.Chat.route) {
            ChatScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
