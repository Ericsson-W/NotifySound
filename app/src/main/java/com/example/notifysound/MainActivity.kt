package com.example.notifysound

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.notifysound.ui.theme.NotifySoundTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun isFirstLaunch(): Boolean {
        return getSharedPreferences("notifysound_setup", MODE_PRIVATE)
            .getBoolean("first_launch", true)
    }

    private fun markFirstLaunchDone() {
        getSharedPreferences("notifysound_setup", MODE_PRIVATE)
            .edit().putBoolean("first_launch", false).apply()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun getSavedTheme(): AppColorTheme {
        val prefs = getSharedPreferences("notifysound_settings", MODE_PRIVATE)
        val name = prefs.getString("color_theme", AppColorTheme.PURPLE.name)
            ?: AppColorTheme.PURPLE.name
        return try { AppColorTheme.valueOf(name) } catch (e: Exception) { AppColorTheme.PURPLE }
    }

    private fun saveTheme(theme: AppColorTheme) {
        getSharedPreferences("notifysound_settings", MODE_PRIVATE)
            .edit().putString("color_theme", theme.name).apply()
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

        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), 1002)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO), 1003)
            }
            if (!hasNotificationPermission()) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001
                )
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 1003
                )
            }
        }

        buildUI()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
            var selectedTheme by remember { mutableStateOf(getSavedTheme()) }

            NotifySoundTheme(colorTheme = selectedTheme) {
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()

                var hasPermission by remember { mutableStateOf(isNotificationAccessGranted()) }
                var isRunning by remember { mutableStateOf(NotificationListener.isConnected) }
                var hasNotifPermission by remember { mutableStateOf(hasNotificationPermission()) }
                var profileCount by remember { mutableStateOf(0) }

                var showProfilesScreen by remember { mutableStateOf(false) }
                var showSetupScreen by remember { mutableStateOf(false) }
                var showOnboarding by remember { mutableStateOf(isFirstLaunch()) }
                var showSoundLibrary by remember { mutableStateOf(false) }

                // Poll state every second
                LaunchedEffect(Unit) {
                    while (true) {
                        isRunning = NotificationListener.isConnected
                        hasPermission = isNotificationAccessGranted()
                        hasNotifPermission = hasNotificationPermission()
                        coroutineScope.launch {
                            profileCount = AppDatabase.getDatabase(context)
                                .contactDao().getContactCount()
                        }
                        delay(1000)
                    }
                }

                val setupPrefs = remember {
                    context.getSharedPreferences("notifysound_setup", MODE_PRIVATE)
                }
                val appsConfigured = listOf(
                    "com.google.android.gm",
                    "com.instagram.android",
                    "com.whatsapp"
                ).count { setupPrefs.getBoolean(it, false) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        showOnboarding -> {
                            OnboardingScreen(
                                hasPermission = hasPermission,
                                isListenerRunning = isRunning,
                                onComplete = {
                                    markFirstLaunchDone()
                                    showOnboarding = false
                                    showProfilesScreen = true
                                }
                            )
                        }

                        showSetupScreen -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(
                                    start = 16.dp, end = 16.dp,
                                    top = 48.dp, bottom = 16.dp
                                )
                            ) {
                                Button(onClick = { showSetupScreen = false }) {
                                    Text("← Back")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                SetupScreen(modifier = Modifier.fillMaxSize())
                            }
                        }

                        showProfilesScreen -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(
                                    start = 16.dp, end = 16.dp,
                                    top = 48.dp, bottom = 16.dp
                                )
                            ) {
                                Button(onClick = { showProfilesScreen = false }) {
                                    Text("← Back")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                ProfilesScreen(modifier = Modifier.fillMaxSize())
                            }
                        }

                        showSoundLibrary -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(
                                    start = 16.dp, end = 16.dp,
                                    top = 48.dp, bottom = 16.dp
                                )
                            ) {
                                Button(onClick = { showSoundLibrary = false }) {
                                    Text("← Back")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                SoundLibraryScreen(modifier = Modifier.fillMaxSize())
                            }
                        }

                        else -> {
                            HomeScreen(
                                isRunning = isRunning,
                                hasPermission = hasPermission,
                                hasNotifPermission = hasNotifPermission,
                                profileCount = profileCount,
                                appsConfigured = appsConfigured,
                                selectedTheme = selectedTheme,
                                onThemeSelected = { theme ->
                                    selectedTheme = theme
                                    saveTheme(theme)
                                },
                                onEnableAccess = {
                                    startActivity(
                                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    )
                                },
                                onReconnect = { toggleListener() },
                                onFixNotifPermission = {
                                    startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                        }
                                    )
                                },
                                onManageProfiles = { showProfilesScreen = true },
                                onSoundLibrary = { showSoundLibrary = true },
                                onAppSetup = { showSetupScreen = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Home Screen ----

@Composable
fun HomeScreen(
    isRunning: Boolean,
    hasPermission: Boolean,
    hasNotifPermission: Boolean,
    profileCount: Int,
    appsConfigured: Int,
    selectedTheme: AppColorTheme,
    onThemeSelected: (AppColorTheme) -> Unit,
    onEnableAccess: () -> Unit,
    onReconnect: () -> Unit,
    onFixNotifPermission: () -> Unit,
    onManageProfiles: () -> Unit,
    onSoundLibrary: () -> Unit,
    onAppSetup: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {

        // ---- Coloured header ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(primary)
                .padding(top = 52.dp, start = 24.dp, end = 24.dp, bottom = 36.dp)
        ) {
            Column {
                Text(
                    "NotifySound",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    "Custom sounds for every person",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                val (statusText, isActive) = when {
                    !hasPermission -> "Tap to enable notification access" to false
                    !isRunning -> "Reconnecting..." to false
                    else -> "Active and listening" to true
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.clickable {
                        when {
                            !hasPermission -> onEnableAccess()
                            !isRunning -> onReconnect()
                            else -> {}
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) Color(0xFF4ADE80) else Color(0xFFFF6B6B)
                                )
                        )
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // ---- White card pulled up ----
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-20).dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Notification permission warning
                if (!hasNotifPermission) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Notification permission needed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onFixNotifPermission) {
                                Text("Fix", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeStatCard(
                        value = "$profileCount",
                        label = "profiles",
                        modifier = Modifier.weight(1f)
                    )
                    HomeStatCard(
                        value = "$appsConfigured",
                        label = "apps active",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HomeSectionLabel("Manage")
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeNavCard(
                        icon = Icons.Default.Person,
                        title = "Profiles",
                        desc = "Per person sounds",
                        onClick = onManageProfiles,
                        modifier = Modifier.weight(1f)
                    )
                    HomeNavCard(
                        icon = Icons.Default.MusicNote,
                        title = "Sounds",
                        desc = "Browse library",
                        onClick = onSoundLibrary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HomeSectionLabel("Settings")
                Spacer(modifier = Modifier.height(8.dp))

                // Settings card — contains both App Setup and Theme Colour
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column {
                        // App Setup row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onAppSetup)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "App setup",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Silence default app sounds",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Theme colour row
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Accent colour",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppColorTheme.values().forEach { theme ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(theme.seed)
                                            .clickable { onThemeSelected(theme) }
                                            .then(
                                                if (selectedTheme == theme)
                                                    Modifier.border(
                                                        3.dp,
                                                        Color.White,
                                                        CircleShape
                                                    )
                                                else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selectedTheme == theme) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "${theme.label} selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
// ---- Home Screen Components ----

@Composable
fun HomeStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HomeSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun HomeNavCard(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HomeNavRow(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}