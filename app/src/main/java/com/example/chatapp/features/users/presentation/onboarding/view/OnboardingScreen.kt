package com.example.chatapp.features.users.presentation.onboarding.view
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chatapp.R
import com.example.chatapp.features.users.presentation.onboarding.viewModel.OnboardingIntent
import com.example.chatapp.features.users.presentation.onboarding.viewModel.OnboardingState
import com.example.chatapp.features.users.presentation.onboarding.viewModel.OnboardingViewModel

@Composable
fun OnboardingRoute(
    onNavigateToChat: () -> Unit,
    onNavigateToCreateProfile: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.navigateToChat) {
        if (!uiState.navigateToChat) return@LaunchedEffect
        onNavigateToChat()
        viewModel.handleIntent(OnboardingIntent.ConsumedChatNavigation)
    }

    LaunchedEffect(uiState.navigateToCreateProfile) {
        if (!uiState.navigateToCreateProfile) return@LaunchedEffect
        onNavigateToCreateProfile()
        viewModel.handleIntent(OnboardingIntent.ConsumedCreateProfileNavigation)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackBarHostState.showSnackbar(message)
        }
    }

    OnboardingScreen(
        uiState = uiState,
        snackBarHostState = snackBarHostState,
        onRetry = { viewModel.handleIntent(OnboardingIntent.Retry) }
    )
}

@Composable
private fun OnboardingScreen(
    uiState: OnboardingState,
    snackBarHostState: SnackbarHostState,
    onRetry: () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge
                        )
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.onboarding_checking),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                uiState.errorMessage != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(onClick = onRetry) {
                            Text(text = stringResource(R.string.onboarding_retry))
                        }
                    }
                }

                else -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}