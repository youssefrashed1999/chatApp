package com.example.chatapp.core.di

import com.example.chatapp.BuildConfig
import com.example.chatapp.core.session.CurrentUserProvider
import com.example.chatapp.features.users.data.repository.CurrentUserProviderImpl
import com.example.chatapp.features.users.data.repository.UserRepositoryImpl
import com.example.chatapp.features.users.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindCurrentUserProvider(impl: CurrentUserProviderImpl): CurrentUserProvider


    companion object {

        @Provides
        @Singleton
        fun provideSupabaseClient(): SupabaseClient =
            createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
                install(Postgrest)
                install(Storage)
                install(Realtime)
            }
    }
}