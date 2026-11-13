package com.aread.app.blocking

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aread.app.MainActivity
import com.aread.app.data.Prefs
import com.aread.app.ui.theme.AreadTheme

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AreadTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BlockScreen(
                        forced = System.currentTimeMillis() < Prefs.forcedLockUntilMs(this),
                        onStudy = {
                            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        },
                        onHome = { goHome(); finish() },
                    )
                }
            }
        }
    }

    private fun goHome() = startActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    @Deprecated("Back goes home, never back into the blocked app")
    override fun onBackPressed() { goHome(); finish() }
}

@Composable
private fun BlockScreen(forced: Boolean, onStudy: () -> Unit, onHome: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            if (forced) "You've been away from your books." else "This one waits until you're done.",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (forced) "Distracting apps are paused until you finish a study session."
            else "Your focus session is still running. Finish it first, then it's all yours.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onStudy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Back to studying") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Go to home screen") }
    }
}
