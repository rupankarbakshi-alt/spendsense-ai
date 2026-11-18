package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "spendsense_settings")

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark Navy"),
    LIGHT("Crisp Light")
}

class ThemePreferences(private val context: Context) {
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[themeModeKey]
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }
}
