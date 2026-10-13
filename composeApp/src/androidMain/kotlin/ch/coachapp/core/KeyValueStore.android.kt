package ch.coachapp.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private class AndroidKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}

@Composable
actual fun rememberKeyValueStore(): KeyValueStore {
    val context = LocalContext.current
    return remember(context) {
        AndroidKeyValueStore(context.getSharedPreferences("coachapp", Context.MODE_PRIVATE))
    }
}
