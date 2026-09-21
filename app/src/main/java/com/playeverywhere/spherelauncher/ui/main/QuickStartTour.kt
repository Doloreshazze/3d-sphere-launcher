package com.playeverywhere.spherelauncher.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.playeverywhere.spherelauncher.R

/** No permissions are requested here. Choosing a HOME app always remains optional. */
@Composable
fun QuickStartTour(
    openHomeSettings: (android.content.Context) -> Unit = ::openDefaultLauncherSettings,
    defaultLauncherStatus: (android.content.Context) -> Boolean = ::isDefaultLauncher,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var setupPage by rememberSaveable { mutableStateOf(false) }
    var isHome by remember { mutableStateOf(defaultLauncherStatus(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isHome = defaultLauncherStatus(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        onDismissRequest = onComplete,
        title = { Text(stringResource(if (setupPage) R.string.quick_home_title else R.string.quick_welcome_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (setupPage) {
                    Text(stringResource(R.string.quick_home_body))
                    Text(stringResource(if (isHome) R.string.quick_home_active else R.string.quick_home_optional))
                    if (!isHome) {
                        Button(
                            onClick = {
                                // Selecting a HOME app immediately brings the chosen launcher to the
                                // foreground. Persist completion first so this dialog does not reopen.
                                onComplete()
                                openHomeSettings(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.quick_choose_home))
                        }
                    }
                } else {
                    Text(stringResource(R.string.quick_controls))
                    Text(stringResource(R.string.quick_privacy), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (setupPage) onComplete() else setupPage = true }) {
                Text(stringResource(if (setupPage) R.string.quick_try_now else R.string.quick_setup))
            }
        },
        dismissButton = {
            TextButton(onClick = { if (setupPage) setupPage = false else onComplete() }) {
                Text(stringResource(if (setupPage) R.string.ob_btn_back else R.string.quick_try_now))
            }
        }
    )
}
