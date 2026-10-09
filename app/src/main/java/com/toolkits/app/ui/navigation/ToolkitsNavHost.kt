package com.toolkits.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.toolkits.app.data.preferences.UserPreferencesRepository
import com.toolkits.app.ui.screens.Base64Screen
import com.toolkits.app.ui.screens.FancyTextScreen
import com.toolkits.app.ui.screens.FileEditorScreen
import com.toolkits.app.ui.screens.FileViewerScreen
import com.toolkits.app.ui.screens.HomeScreen
import com.toolkits.app.ui.screens.ProjectsScreen
import com.toolkits.app.ui.screens.SettingsScreen
import com.toolkits.app.ui.screens.TextInfoScreen
import com.toolkits.app.ui.screens.TextToolsHubScreen
import com.toolkits.app.ui.screens.ZipToolsScreen

@Composable
fun ToolkitsNavHost(prefs: UserPreferencesRepository) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onZipTools = { nav.navigate(Routes.ZIP_TOOLS) },
                onTextTools = { nav.navigate(Routes.TEXT_HUB) },
                onSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.ZIP_TOOLS) { ZipToolsScreen(onBack = { nav.popBackStack() }, prefs = prefs) }
        composable(Routes.TEXT_HUB) {
            TextToolsHubScreen(
                onBack = { nav.popBackStack() },
                onBase64 = { nav.navigate(Routes.BASE64) },
                onProjects = { nav.navigate(Routes.PROJECTS) },
                onTextInfo = { nav.navigate(Routes.TEXT_INFO) },
                onFancy = { nav.navigate(Routes.FANCY) }
            )
        }
        composable(Routes.BASE64) { Base64Screen(onBack = { nav.popBackStack() }) }
        composable(Routes.TEXT_INFO) {
            TextInfoScreen(
                onBack = { nav.popBackStack() },
                onViewFile = { uri, name -> nav.navigate(Routes.fileViewer(uri, name)) }
            )
        }
        composable(
            Routes.FILE_VIEWER,
            arguments = listOf(navArgument("uri") { defaultValue = ""; type = NavType.StringType }, navArgument("name") { defaultValue = ""; type = NavType.StringType })
        ) { backStack ->
            FileViewerScreen(
                onBack = { nav.popBackStack() },
                uriString = backStack.arguments?.getString("uri").orEmpty(),
                fileName = backStack.arguments?.getString("name").orEmpty()
            )
        }
        composable(
            Routes.FILE_EDITOR,
            arguments = listOf(navArgument("path") { defaultValue = ""; type = NavType.StringType })
        ) { backStack ->
            FileEditorScreen(onBack = { nav.popBackStack() }, filePath = backStack.arguments?.getString("path").orEmpty())
        }
        composable(Routes.PROJECTS) {
            ProjectsScreen(onBack = { nav.popBackStack() }, onOpenEditor = { path -> nav.navigate(Routes.fileEditor(path)) })
        }
        composable(Routes.FANCY) { FancyTextScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, prefs = prefs) }
    }
}
