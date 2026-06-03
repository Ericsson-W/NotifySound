package com.example.notifysound

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.notifysound.ui.theme.NotifySoundTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        )
        return flat?.contains(packageName) == true
    }

    private fun toggleListener() {
        val component = ComponentName(this, NotificationListener::class.java)
        packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationListenerService.requestRebind(component)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        if (isNotificationAccessGranted() && !NotificationListener.isConnected) {
            toggleListener()
        }
        buildUI()
    }

    private fun buildUI() {
        setContent {
            NotifySoundTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var hasPermission by remember { mutableStateOf(isNotificationAccessGranted()) }
                    var isRunning by remember { mutableStateOf(NotificationListener.isConnected) }

                    // Poll every 500ms until connected
                    LaunchedEffect(Unit) {
                        while (!isRunning) {
                            delay(500)
                            isRunning = NotificationListener.isConnected
                            hasPermission = isNotificationAccessGranted()
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when {
                                !hasPermission -> "❌ Notification Access Required"
                                !isRunning     -> "⚠️ Granted but reconnecting..."
                                else           -> "✅ Active and listening"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!hasPermission) {
                            Button(onClick = {
                                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                            }) {
                                Text("Enable Notification Access")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (hasPermission && !isRunning) {
                            Button(onClick = {
                                toggleListener()
                            }) {
                                Text("Reconnect Listener")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Button(onClick = {
                            val mp = MediaPlayer.create(applicationContext, R.raw.fahh)
                            mp?.setOnCompletionListener { it.release() }
                            mp?.start()
                        }) {
                            Text("Play Test Sound")
                        }
                    }
                }
            }
        }
    }
}