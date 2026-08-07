package com.example.notifysound

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

data class AppSetupItem(
    val label: String,
    val packageName: String,
    val steps: List<String>
)

val appsToSetup = listOf(
    AppSetupItem(
        label = "Gmail",
        packageName = "com.google.android.gm",
        steps = listOf(
            "Tap 'Open Gmail Notification Settings' below",
            "Tap on a notification category (e.g. 'New mail')",
            "Tap 'Sound' and select 'None'",
            "Repeat for any other categories listed"
        )
    ),
    AppSetupItem(
        label = "Instagram",
        packageName = "com.instagram.android",
        steps = listOf(
            "Tap 'Open Instagram Notification Settings' below",
            "Tap 'Sound' and select 'None'",
            "If you see categories, repeat for each one"
        )
    )
)

@Composable
fun SetupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(
            "notifysound_setup",
            android.content.Context.MODE_PRIVATE
        )
    }

    // Load persisted state — updated automatically when notifications arrive
    var setupState by remember {
        mutableStateOf(
            appsToSetup.associate { app ->
                app.packageName to prefs.getBoolean(app.packageName, false)
            }
        )
    }

    // Re-check state when screen is shown
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            setupState = appsToSetup.associate { app ->
                app.packageName to prefs.getBoolean(app.packageName, false)
            }
        }
    }

    val allSetUp = setupState.values.all { it }

    LazyColumn(modifier = modifier.padding(16.dp)) {

        item {
            Text("Setup", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Follow the steps below for each app. " +
                        "NotifySound will automatically confirm once each app is configured.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Overall status banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (allSetUp)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (allSetUp)
                            Icons.Default.CheckCircle
                        else
                            Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (allSetUp)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (allSetUp)
                                "All set up!"
                            else
                                "Setup required",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (allSetUp)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (allSetUp)
                                "NotifySound is fully active"
                            else
                                "Complete the steps below",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allSetUp)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        items(appsToSetup.size) { index ->
            val app = appsToSetup[index]
            val isSetUp = setupState[app.packageName] == true

            val appIcon = remember(app.packageName) {
                try {
                    context.packageManager
                        .getApplicationIcon(app.packageName)
                        .toBitmap()
                        .asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSetUp)
                        MaterialTheme.colorScheme.surfaceVariant
                    else
                        MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = if (isSetUp)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    // Header row — icon, name, status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = "${app.label} icon",
                                modifier = Modifier.size(40.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    app.label.first().toString(),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (isSetUp) "Configured ✓" else "Not configured",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSetUp)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }

                        Icon(
                            imageVector = if (isSetUp)
                                Icons.Default.CheckCircle
                            else
                                Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSetUp)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isSetUp) {
                        app.steps.forEachIndexed { i, step ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "${i + 1}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(20.dp)
                                )
                                Text(
                                    step,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isSetUp)
                                "Open ${app.label} Notification Settings ↻"
                            else
                                "Open ${app.label} Notification Settings →"
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        if (isSetUp)
                            "Configured. You can reopen settings anytime to change notification behaviour."
                        else
                            "NotifySound will automatically confirm once you've silenced the sound.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Why is this needed?",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Android doesn't allow apps to change each other's notification " +
                                "sounds directly. By silencing the app yourself, NotifySound can " +
                                "then play the right sound for each person — custom sounds for " +
                                "people in your profiles, and your default sound for everyone else.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}