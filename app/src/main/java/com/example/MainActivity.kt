package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.ThemePreferences
import com.example.firebase.configureAppCheck
import com.example.ui.navigation.SpendSenseApp
import com.example.ui.theme.SpendSenseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val themePreferences = remember { ThemePreferences(applicationContext) }
            val themeMode by themePreferences.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = ThemeMode.DARK
            )
            val scope = rememberCoroutineScope()

            SpendSenseTheme(themeMode = themeMode) {
                SpendSenseApp(
                    themeMode = themeMode,
                    onSelectThemeMode = { mode ->
                        scope.launch { themePreferences.setThemeMode(mode) }
                    }
                )
            }
        }
    }
}
