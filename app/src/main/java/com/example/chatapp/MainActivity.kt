package com.example.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chatapp.core.Constants.Navigation.CHAT_ROUTE
import com.example.chatapp.core.Constants.Navigation.CREATE_PROFILE_ROUTE
import com.example.chatapp.core.Constants.Navigation.ONBOARDING_ROUTE
import com.example.chatapp.features.chat.presentation.view.ChatRoute
import com.example.chatapp.features.users.presentation.createProfile.view.CreateProfileRoute
import com.example.chatapp.features.users.presentation.onboarding.view.OnboardingRoute
import com.example.chatapp.ui.theme.ChatAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatAppNavHost()
                }
            }
        }
    }
}

@Composable
private fun ChatAppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ONBOARDING_ROUTE) {
        composable(ONBOARDING_ROUTE) {
            OnboardingRoute(
                onNavigateToChat = {
                    navController.navigate(CHAT_ROUTE) {
                        popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                    }
                },
                onNavigateToCreateProfile = {
                    navController.navigate(CREATE_PROFILE_ROUTE) {
                        popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                    }
                }
            )
        }
        composable(CREATE_PROFILE_ROUTE) {
            CreateProfileRoute(
                onNavigateToChat = {
                    navController.navigate(CHAT_ROUTE) {
                        popUpTo(CREATE_PROFILE_ROUTE) { inclusive = true }
                    }
                }
            )
        }
        composable(CHAT_ROUTE) {
            ChatRoute()
        }
    }
}