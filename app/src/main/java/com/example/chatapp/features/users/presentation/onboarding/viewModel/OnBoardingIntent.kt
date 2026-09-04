package com.example.chatapp.features.users.presentation.onboarding.viewModel

sealed class OnboardingIntent {
    data object CheckUser : OnboardingIntent()
    data object Retry : OnboardingIntent()
    data object ConsumedChatNavigation : OnboardingIntent()
    data object ConsumedCreateProfileNavigation : OnboardingIntent()
}