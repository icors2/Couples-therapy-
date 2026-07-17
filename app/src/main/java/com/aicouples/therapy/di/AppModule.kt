package com.aicouples.therapy.di

import android.content.Context
import com.aicouples.therapy.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideSupabaseClient(json: Json): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL.ifBlank { "https://placeholder.supabase.co" }
        val key = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "placeholder-anon-key" }

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key,
        ) {
            defaultSerializer = KotlinXSerializer(json)
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Functions)
            install(Storage)
        }
    }

    @Provides
    @Singleton
    fun provideAppContext(@ApplicationContext context: Context): Context = context
}
