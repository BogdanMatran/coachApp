package ch.coachapp.core

import ch.coachapp.core.config.AppConfig
import platform.Foundation.NSProcessInfo

/**
 * The simulator shares the Mac's network, so 127.0.0.1 works there. A physical iPhone
 * reaches a local Supabase stack through the Mac's Bonjour name instead.
 */
actual fun resolveSupabaseUrl(url: String): String {
    val isSimulator = NSProcessInfo.processInfo.environment["SIMULATOR_UDID"] != null
    if (isSimulator || AppConfig.DEV_HOST.isEmpty()) return url
    return url.replace("://127.0.0.1", "://${AppConfig.DEV_HOST}").replace("://localhost", "://${AppConfig.DEV_HOST}")
}
