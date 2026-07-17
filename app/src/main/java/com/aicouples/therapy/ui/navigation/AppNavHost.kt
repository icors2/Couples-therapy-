package com.aicouples.therapy.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aicouples.therapy.ui.auth.AuthScreen
import com.aicouples.therapy.ui.auth.AuthViewModel
import com.aicouples.therapy.ui.history.HistoryScreen
import com.aicouples.therapy.ui.home.HomeScreen
import com.aicouples.therapy.ui.pairing.PairingScreen
import com.aicouples.therapy.ui.settings.SettingsScreen
import com.aicouples.therapy.ui.therapy.SessionInviteScreen
import com.aicouples.therapy.ui.therapy.TherapyScreen

@Composable
fun AppNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(authState.destination) {
        when (val dest = authState.destination) {
            AuthDestination.Loading -> Unit
            AuthDestination.Auth -> {
                navController.navigate(Routes.AUTH) {
                    popUpTo(0) { inclusive = true }
                }
            }
            AuthDestination.Pairing -> {
                navController.navigate(Routes.PAIRING) {
                    popUpTo(0) { inclusive = true }
                }
            }
            AuthDestination.Home -> {
                navController.navigate(Routes.HOME) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthDestination.SessionInvite -> {
                navController.navigate(Routes.sessionInvite(dest.sessionId))
                authViewModel.consumeDestination()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        composable(Routes.AUTH) {
            AuthScreen(
                state = authState,
                onGoogleSignIn = authViewModel::signInWithGoogle,
            )
        }
        composable(Routes.PAIRING) {
            PairingScreen(
                onPaired = { authViewModel.refreshProfile() },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onStartTherapy = { sessionId ->
                    navController.navigate(Routes.therapy(sessionId))
                },
                onOpenInvite = { sessionId ->
                    navController.navigate(Routes.sessionInvite(sessionId))
                },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onNeedsPairing = {
                    navController.navigate(Routes.PAIRING) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
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
                onEnded = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.SESSION_INVITE,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            SessionInviteScreen(
                sessionId = sessionId,
                onJoined = { navController.navigate(Routes.therapy(sessionId)) },
                onDeclined = { navController.popBackStack() },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onOpenSession = { sessionId ->
                    navController.navigate(Routes.therapy(sessionId))
                },
                onBack = { navController.popBackStack() },
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
            )
        }
    }
}

sealed interface AuthDestination {
    data object Loading : AuthDestination
    data object Auth : AuthDestination
    data object Pairing : AuthDestination
    data object Home : AuthDestination
    data class SessionInvite(val sessionId: String) : AuthDestination
}
