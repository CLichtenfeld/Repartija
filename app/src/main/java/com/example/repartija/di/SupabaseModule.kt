package com.example.repartija.di

import com.example.repartija.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        // Sanitizamos la URL para eliminar el sufijo /rest/v1 si existe
        val sanitizedUrl = BuildConfig.SUPABASE_URL
            .removeSuffix("/rest/v1")
            .removeSuffix("/rest/v1/")
            .removeSuffix("/")

        return createSupabaseClient(
            supabaseUrl = sanitizedUrl,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}
