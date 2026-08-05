package dev.reprotrail.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** Renders the generated application shell. */
@Composable
fun StarterApp(modifier: Modifier = Modifier) {
    StarterScreenTemplate(modifier = modifier) { contentPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.app_ready_message),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

/** Provides the adaptive scaffold used by the application shell. */
@Composable
fun StarterScreenTemplate(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            content = content,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.safeDrawing,
        )
    }
}
