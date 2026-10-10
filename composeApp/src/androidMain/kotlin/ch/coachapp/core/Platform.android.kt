package ch.coachapp.core

// The Android emulator reaches the host machine through 10.0.2.2.
actual fun resolveSupabaseUrl(url: String): String =
    url.replace("://127.0.0.1", "://10.0.2.2").replace("://localhost", "://10.0.2.2")
