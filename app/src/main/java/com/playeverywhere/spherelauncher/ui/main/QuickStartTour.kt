package com.playeverywhere.spherelauncher.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.playeverywhere.spherelauncher.R

/** No permissions are requested here. Choosing a HOME app always remains optional. */
@Composable
fun QuickStartTour(
    openHomeSettings: (android.content.Context) -> Unit = ::openDefaultLauncherSettings,
    onComplete: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onComplete,
        title = { Text(stringResource(R.string.quick_welcome_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.quick_controls))
                Text(stringResource(R.string.quick_privacy), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Persist completion before Android switches to its HOME-app settings page.
                    onComplete()
                    openHomeSettings(context)
                }
            ) {
                Text(stringResource(R.string.quick_setup))
            }
        },
        dismissButton = {
            TextButton(onClick = onComplete) {
                Text(stringResource(R.string.quick_try_now))
            }
        }
    )
}
