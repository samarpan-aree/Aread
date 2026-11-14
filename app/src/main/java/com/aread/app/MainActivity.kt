package com.aread.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aread.app.data.Prefs
import com.aread.app.data.Supabase
import io.github.jan.supabase.auth.handleDeeplinks
import com.aread.app.ui.nav.AreadNav
import com.aread.app.ui.theme.AreadColors
import com.aread.app.ui.theme.AreadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runCatching { Supabase.client.handleDeeplinks(intent) }
        setContent {
            AreadTheme {
                Surface(Modifier.fillMaxSize(), color = AreadColors.Paper) {
                    AreadNav()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runCatching { Supabase.client.handleDeeplinks(intent) }
    }

    override fun onResume() {
        super.onResume()
        Prefs.touchOpen(this)
    }
}
