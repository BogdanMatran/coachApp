package ch.coachapp.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

private class IosKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun getString(key: String): String? = defaults.stringForKey(key)
    override fun putString(key: String, value: String) = defaults.setObject(value, key)
}

@Composable
actual fun rememberKeyValueStore(): KeyValueStore = remember { IosKeyValueStore() }
