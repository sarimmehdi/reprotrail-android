package dev.reprotrail.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.reprotrail.ui.theme.ReproTrailTheme

/** Hosts the generated Compose application. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReproTrailTheme {
                StarterApp()
            }
        }
    }
}
