package com.aread.app.data

import com.aread.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object Supabase {
    val client by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            install(Auth) {
                scheme = "aread"
                host = "auth"
            }
            install(Postgrest)
            install(Storage)
            install(Functions)
        }
    }
}
