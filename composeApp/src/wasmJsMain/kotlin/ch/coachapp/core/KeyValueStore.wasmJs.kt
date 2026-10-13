package ch.coachapp.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.localStorage

private class BrowserKeyValueStore : KeyValueStore {
    override fun getString(key: String): String? = localStorage.getItem(key)
    override fun putString(key: String, value: String) = localStorage.setItem(key, value)
}

@Composable
actual fun rememberKeyValueStore(): KeyValueStore = remember { BrowserKeyValueStore() }
