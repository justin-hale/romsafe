package app.romsafe.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.romsafe.android.R
import app.romsafe.shared.api.IncompatibleVersionException.Side
import app.romsafe.shared.api.VersionProblem

private const val RELEASES = "https://github.com/justin-hale/romsafe/releases/latest"

/** Shown instead of the app when it and the server can't work together. */
@Composable
fun UpdateScreen(problem: VersionProblem, onRetry: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(painterResource(R.drawable.logo), contentDescription = null, Modifier.size(72.dp))
            Text(
                if (problem.update == Side.APP) "Update the app" else "Update the server",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                problem.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                if (problem.update == Side.APP) {
                    "Your saves are safe on both sides. Nothing syncs until the app is updated."
                } else {
                    "Your saves are safe on both sides. Nothing syncs until the server is updated."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (problem.update == Side.APP) {
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES))) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Get the latest app") }
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            } else {
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Try again") }
            }
        }
    }
}
