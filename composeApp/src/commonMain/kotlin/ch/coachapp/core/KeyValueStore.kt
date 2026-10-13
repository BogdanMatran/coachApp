package ch.coachapp.core

import androidx.compose.runtime.Composable

/**
 * The small amount of device-local state the app keeps outside Supabase — currently just the
 * chosen theme. Deliberately not a new dependency: it is a handful of strings, and each platform
 * already has somewhere obvious to put them.
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/**
 * Read during composition rather than from a global, so Android can take the context from
 * `LocalContext` and no platform needs an init call from its entry point. The first composition
 * already sees the stored value, so a persisted theme does not flash the wrong one on launch.
 */
@Composable
expect fun rememberKeyValueStore(): KeyValueStore
