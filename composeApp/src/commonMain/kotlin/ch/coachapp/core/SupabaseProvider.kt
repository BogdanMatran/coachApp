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

/** The public bucket holding the exercise thumbnails and animations. */
const val ExerciseMediaBucket = "exercise-media"

/**
 * Resolves a filename stored in `exercises.image_path` / `gif_path` to a public URL. The bucket is
 * public, so there is nothing to sign and no round trip: the rows carry bare filenames, which keeps
 * moving or re-hosting the media a one-line change here rather than a migration over 1,324 rows.
 * Populate the bucket with scripts/sync-exercise-media.sh.
 */
fun exerciseMediaUrl(path: String, folder: String): String =
    "${resolveSupabaseUrl(AppConfig.SUPABASE_URL).trimEnd('/')}" +
        "/storage/v1/object/public/$ExerciseMediaBucket/$folder/$path"

fun exerciseImageUrl(path: String): String = exerciseMediaUrl(path, "img")

fun exerciseGifUrl(path: String): String = exerciseMediaUrl(path, "gif")
