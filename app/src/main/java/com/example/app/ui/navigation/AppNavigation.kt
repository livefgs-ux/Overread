package com.example.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.app.ui.screens.HomeScreen
import com.example.app.ui.screens.LanguageSelectorScreen
import com.example.app.ui.screens.OnboardingScreen
import com.example.data.UserPreferencesRepository

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val repository = remember { UserPreferencesRepository(context) }
    
    // We collect the first emitted value to decide start destination. 
    // In a real production app we might use a splash screen while loading to avoid blink.
    val hasCompletedOnboarding by repository.onboardingCompletedFlow.collectAsState(initial = null)

    if (hasCompletedOnboarding == null) {
        // Still loading preferences, you could show a loading screen here.
        return
    }

    val startDestination = if (hasCompletedOnboarding == true) {
        "home"
    } else {
        "onboarding"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onFinish = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            HomeScreen(
                onNavigateToLanguageSelector = {
                    navController.navigate("language_selector")
                }
            )
        }
        composable("language_selector") {
            LanguageSelectorScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
