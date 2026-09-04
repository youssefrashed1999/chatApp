package com.example.chatapp.features.users.presentation.onboarding.viewModel

data class OnboardingState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val navigateToChat: Boolean = false,
    val navigateToCreateProfile: Boolean = false,
)