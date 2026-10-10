package ch.coachapp.core

import ch.coachapp.core.config.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

fun createAppSupabaseClient(): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = resolveSupabaseUrl(AppConfig.SUPABASE_URL),
        supabaseKey = AppConfig.SUPABASE_ANON_KEY,
    ) {
        defaultSerializer = KotlinXSerializer(AppJson)
        install(Auth)
        install(Postgrest)
        install(Storage)
    }

/** Lets each platform rewrite the configured URL, e.g. localhost -> emulator host loopback on Android. */
expect fun resolveSupabaseUrl(url: String): String
