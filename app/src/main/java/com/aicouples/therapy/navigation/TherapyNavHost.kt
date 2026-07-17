package com.aicouples.therapy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aicouples.therapy.auth.AuthScreen
import com.aicouples.therapy.auth.AuthViewModel
import com.aicouples.therapy.history.HistoryScreen
import com.aicouples.therapy.home.HomeScreen
import com.aicouples.therapy.pairing.PairingScreen
import com.aicouples.therapy.settings.SettingsScreen
import com.aicouples.therapy.therapy.ui.TherapyScreen

@Composable
fun TherapyNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(authState.isAuthenticated, authState.isPaired, authState.isLoading) {
        if (authState.isLoading) return@LaunchedEffect
        val target = when {
            !authState.isAuthenticated -> Routes.AUTH
            !authState.isPaired -> Routes.PAIRING
            else -> Routes.HOME
        }
        val current = navController.currentDestination?.route
        if (current != target &&
            current != Routes.THERAPY &&
            current != Routes.HISTORY &&
            current != Routes.SETTINGS
        ) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            // Auth bootstrap handled by AuthViewModel + LaunchedEffect above
        }
        composable(Routes.AUTH) {
            AuthScreen(
                state = authState,
                onGoogleSignIn = authViewModel::signInWithGoogle,
            )
        }
        composable(Routes.PAIRING) {
            PairingScreen(
                onPaired = {
                    authViewModel.refreshProfile()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PAIRING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onStartTherapy = { sessionId ->
                    navController.navigate(Routes.therapy(sessionId))
                },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenSession = { sessionId ->
                    navController.navigate(Routes.therapy(sessionId))
                },
            )
        }
        composable(
            route = Routes.THERAPY,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            TherapyScreen(
                sessionId = sessionId,
                onLeave = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId ->
                    navController.navigate(Routes.therapy(sessionId))
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onUnpaired = {
                    authViewModel.refreshProfile()
                    navController.navigate(Routes.PAIRING) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
