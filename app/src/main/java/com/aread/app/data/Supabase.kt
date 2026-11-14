package com.aread.app.data

import com.aread.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlin.time.Duration.Companion.seconds

object Supabase {
    val client by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            requestTimeout = 120.seconds
            httpConfig {
                install(io.ktor.client.plugins.HttpTimeout) {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 20_000
                    socketTimeoutMillis = 120_000
                }
            }
            install(Auth) {
                scheme = "aread"
                host = "auth"
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
            install(Postgrest)
            install(Storage)
            install(Functions)
        }
    }
}
