package com.example.notifysound

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    hasPermission: Boolean,
    isListenerRunning: Boolean,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(
            "notifysound_setup",
            Context.MODE_PRIVATE
        )
    }

    var currentStep by remember { mutableIntStateOf(0) }

    // Re-read setup state every second
    var setupState by remember {
        mutableStateOf(
            appsToSetup.associate { app ->
                app.packageName to prefs.getBoolean(app.packageName, false)
            }
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            setupState = appsToSetup.associate { app ->
                app.packageName to prefs.getBoolean(app.packageName, false)
            }
        }
    }

    // Auto-advance past permission step once granted
    LaunchedEffect(hasPermission, isListenerRunning) {
        if (currentStep == 0 && hasPermission && isListenerRunning) {
            delay(800) // brief pause so user sees the green tick
            currentStep = 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "NotifySound",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Custom notification sounds for every person",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Step indicator
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { index ->
                Surface(
                    modifier = Modifier
                        .size(if (index == currentStep) 12.dp else 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (index <= currentStep)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline
                ) {}
                if (index < 1) Spacer(modifier = Modifier.width(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        when (currentStep) {

            // ---- Step 0: Notification Access ----
            0 -> {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Step 1 — Grant Access",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "NotifySound needs permission to listen to notifications " +
                            "so it can play the right sound for each person.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Status card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasPermission && isListenerRunning)
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
                            imageVector = if (hasPermission && isListenerRunning)
                                Icons.Default.CheckCircle
                            else
                                Icons.Default.Notifications,
                            contentDescription = null,
                            tint = if (hasPermission && isListenerRunning)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when {
                                hasPermission && isListenerRunning ->
                                    "✅ Access granted — moving on..."
                                hasPermission ->
                                    "⏳ Permission granted, listener connecting..."
                                else ->
                                    "❌ Notification access not granted"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!hasPermission) {
                    Text(
                        "Tap the button below, find NotifySound in the list, " +
                                "and toggle it on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Notification Access Settings →")
                    }
                }

                if (hasPermission && !isListenerRunning) {
                    Button(
                        onClick = { currentStep = 1 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue →")
                    }
                }
            }

            // ---- Step 1: Per-App Setup ----
            1 -> {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Step 2 — Silence App Sounds",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Silence each app's notification sound. " +
                            "NotifySound will play the right sound for each person instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                appsToSetup.forEach { app ->
                    val isSetUp = setupState[app.packageName] == true
                    AppSetupCard(
                        app = app,
                        isSetUp = isSetUp,
                        context = context
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "The app will automatically confirm ✅ once it detects " +
                            "each app's sound has been silenced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val allDone = setupState.values.all { it }
                    Text(if (allDone) "All done — Get Started! →" else "Continue to App →")
                }

                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Skip for now")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun AppSetupCard(
    app: AppSetupItem,
    isSetUp: Boolean,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSetUp)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isSetUp)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                if (isSetUp) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!isSetUp) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Instructions
                Text(
                    "How to silence ${app.label}:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

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
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Open settings button
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
                    Text("Open ${app.label} Notification Settings →")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Send test notification button
                OutlinedButton(
                    onClick = { sendTestForApp(context, app.packageName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(testButtonLabel(app.packageName))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    testButtonHint(app.packageName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Notification sound silenced — NotifySound is active for ${app.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

fun sendTestForApp(context: Context, packageName: String) {
    when (packageName) {
        "com.google.android.gm" -> {
            // Open Gmail compose to self
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_SUBJECT, "NotifySound Test")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "This is a test email to verify NotifySound setup. " +
                                "You can delete this."
                    )
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Gmail not installed — open play store
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.gm")
                )
                context.startActivity(intent)
            }
        }
        "com.instagram.android" -> {
            // Open Instagram DMs
            try {
                val intent = context.packageManager
                    .getLaunchIntentForPackage("com.instagram.android")
                if (intent != null) context.startActivity(intent)
            } catch (e: Exception) {
                // Instagram not installed
            }
        }
    }
}

fun testButtonLabel(packageName: String): String {
    return when (packageName) {
        "com.google.android.gm" -> "Send myself a test email →"
        "com.instagram.android" -> "Open Instagram to get a test message →"
        else -> "Send test notification →"
    }
}

fun testButtonHint(packageName: String): String {
    return when (packageName) {
        "com.google.android.gm" ->
            "Send yourself an email — if no default sound plays, you're set up correctly."
        "com.instagram.android" ->
            "Ask someone to send you a message. If no default sound plays, you're good."
        else -> ""
    }
}