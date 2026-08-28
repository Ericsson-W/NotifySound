package com.example.notifysound

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.app.NotificationCompat

class NotificationListener : NotificationListenerService() {

    companion object {
        var isConnected = false
        private val playedKeys = mutableSetOf<String>()
        private val keyMapping = mutableMapOf<String, String>()
        private var listenerConnectedTime: Long = 0
        private val lastPlayedBySender = mutableMapOf<String, Long>()
        private const val SENDER_COOLDOWN_MS = 500L
        private var currentPlayer: MediaPlayer? = null
        private const val CHANNEL_ID = "notifysound_service"
        private const val FOREGROUND_ID = 1
    }

    private val managedApps = setOf(
        "com.google.android.gm",
        "com.instagram.android",
        "com.whatsapp",
        "org.telegram.messenger"  // add this
    )

    private val summaryTitles = mapOf(
        "com.whatsapp" to setOf("WhatsApp"),
        "com.instagram.android" to setOf("Instagram"),
        "org.telegram.messenger" to setOf("Telegram")
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- Foreground Service ----

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NotifySound Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps NotifySound running in the background"
                setShowBadge(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("NotifySound is active")
            .setContentText("Listening for notifications")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)        // ← add this
            .setSilent(true)             // ← add this
            .build()
    }

    // ---- Lifecycle ----

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        listenerConnectedTime = System.currentTimeMillis()
        Log.d("NotifySound", "CONNECTED")

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_ID,
                buildForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FOREGROUND_ID, buildForegroundNotification())
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.d("NotifySound", "DISCONNECTED")
    }

    // ---- Audio ----

    private fun suppressChannelSound() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            mainHandler.postDelayed({
                try {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_NOTIFICATION, originalVolume, 0
                    )
                } catch (e: Exception) {
                    Log.e("NotifySound", "Failed to restore volume: ${e.message}")
                }
            }, 2000)
        } catch (e: Exception) {
            Log.e("NotifySound", "suppressChannelSound failed: ${e.message}")
        }
    }

    private fun playCustomSound(soundFileName: String) {
        try {
            currentPlayer?.let {
                try { if (it.isPlaying) it.stop(); it.release() } catch (e: Exception) { }
                currentPlayer = null
            }

            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )

            if (soundFileName.startsWith("content://") ||
                soundFileName.startsWith("file://")) {
                mp.setDataSource(applicationContext, android.net.Uri.parse(soundFileName))
            } else {
                val afd = applicationContext.resources.openRawResourceFd(
                    resolveSoundFileName(soundFileName)
                )
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }

            mp.setOnCompletionListener { it.release(); currentPlayer = null }
            mp.prepare()
            mp.start()
            currentPlayer = mp
            Log.d("NotifySound", "Playing: $soundFileName")
        } catch (e: Exception) {
            Log.e("NotifySound", "Sound failed: ${e.message}")
        }
    }

    private fun playDefaultNotificationSound() {
        try {
            currentPlayer?.let {
                try { if (it.isPlaying) it.stop(); it.release() } catch (e: Exception) { }
                currentPlayer = null
            }
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, defaultUri)
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        } catch (e: Exception) {
            Log.e("NotifySound", "Default sound failed: ${e.message}")
        }
    }

    private fun resolveSoundFileName(fileName: String): Int {
        return when (fileName) {
            "fahh" -> R.raw.fahh
            "bruh" -> R.raw.bruh
            "fornite" -> R.raw.fornite
            "phub" -> R.raw.phub
            "italian_brainrot_rington" -> R.raw.italian_brainrot_ringtone
            "taco_bell_bond" -> R.raw.taco_bell_bong
            "bing_chilling" -> R.raw.bing_chilling
            "lego_breaking" -> R.raw.lego_breaking
            "bonk" -> R.raw.bonk
            else -> R.raw.fahh
        }
    }

    // ---- Notification Helpers ----

    private fun isSummaryNotification(sbn: StatusBarNotification): Boolean {
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val summaries = summaryTitles[sbn.packageName] ?: return false
        return title in summaries
    }

    private fun autoVerifySetup(sbn: StatusBarNotification) {
        val sound = sbn.notification.sound
        val defaults = sbn.notification.defaults
        val hasDefaultSound = (defaults and Notification.DEFAULT_SOUND) != 0
        val isSilent = sound == null && !hasDefaultSound

        if (sbn.packageName == "com.google.android.gm" && isSilent) {
            val prefs = applicationContext.getSharedPreferences(
                "notifysound_setup",
                android.content.Context.MODE_PRIVATE
            )
            if (!prefs.getBoolean(sbn.packageName, false)) {
                prefs.edit().putBoolean(sbn.packageName, true).apply()
                Log.d("NotifySound", "Auto-verified: ${sbn.packageName}")
            }
        }
    }

    // ---- Sender Extraction ----

    private fun getGmailSenderEmail(extras: android.os.Bundle): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val people = extras.getParcelableArrayList<android.app.Person>(
                    Notification.EXTRA_PEOPLE_LIST
                )
                val uri = people?.firstOrNull()?.uri
                if (!uri.isNullOrEmpty()) return uri.removePrefix("mailto:")
            } else {
                val people = extras.getParcelableArrayList<android.os.Parcelable>(
                    Notification.EXTRA_PEOPLE_LIST
                )
                people?.firstOrNull()?.let { person ->
                    val uri = person.javaClass.getMethod("getUri").invoke(person) as? String
                    if (!uri.isNullOrEmpty()) return uri.removePrefix("mailto:")
                }
            }
            ""
        } catch (e: Exception) { "" }
    }

    private fun getInstagramSenderId(sbn: StatusBarNotification): String {
        return sbn.notification.extras.getString(
            "com.instagram.android.igns.logging.sender_id"
        ) ?: ""
    }

    private fun resolveWhatsAppPhoneNumber(contactUri: String): String {
        return try {
            val uri = android.net.Uri.parse(contactUri)
            val cursor = applicationContext.contentResolver.query(
                uri, null, null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val contactId = it.getLong(
                        it.getColumnIndexOrThrow(
                            android.provider.ContactsContract.Contacts._ID
                        )
                    )
                    val phoneCursor = applicationContext.contentResolver.query(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId.toString()),
                        null
                    )
                    phoneCursor?.use { pc ->
                        if (pc.moveToFirst()) {
                            return pc.getString(0)
                                .replace(" ", "")
                                .replace("-", "")
                                .replace("(", "")
                                .replace(")", "")
                        }
                    }
                }
            }
            ""
        } catch (e: Exception) { "" }
    }

    private fun getNotificationIdentifier(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""

        return when (sbn.packageName) {
            "com.google.android.gm" -> {
                val senderEmail = getGmailSenderEmail(extras)
                if (senderEmail.isNotEmpty()) senderEmail else title
            }
            "com.instagram.android" -> title
            "com.whatsapp" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val peopleList = extras.getParcelableArrayList<android.app.Person>(
                        Notification.EXTRA_PEOPLE_LIST
                    )
                    val contactUri = peopleList?.firstOrNull()?.uri ?: ""
                    if (contactUri.isNotEmpty() && contactUri.startsWith("content://")) {
                        val phoneNumber = resolveWhatsAppPhoneNumber(contactUri)
                        if (phoneNumber.isNotEmpty()) return phoneNumber
                    }
                }
                title
            }
            "org.telegram.messenger" -> {
                // Try to get phone number from people list first
                val people = extras.getStringArray(Notification.EXTRA_PEOPLE)
                if (!people.isNullOrEmpty()) {
                    val uri = people[0]
                    if (uri.startsWith("tel:")) {
                        val number = uri.removePrefix("tel:")
                        Log.d("NotifySound", "Telegram PHONE=$number")
                        return number
                    }
                }
                // Fall back to title (display name)
                Log.d("NotifySound", "Telegram TITLE=$title")
                title
            }
            else -> title
        }
    }

    private fun buildNotifKey(sbn: StatusBarNotification): String {
        return when (sbn.packageName) {
            "com.instagram.android" -> "${sbn.key}_${sbn.notification.`when`}"
            "com.google.android.gm" -> {
                val extras = sbn.notification.extras
                val senderEmail = getGmailSenderEmail(extras)
                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val identifier = if (senderEmail.isNotEmpty()) senderEmail else title
                "${sbn.packageName}_$identifier"
            }
            "com.whatsapp" -> "${sbn.key}_${sbn.notification.`when`}"
            "org.telegram.messenger" -> {
                val people = sbn.notification.extras.getStringArray(Notification.EXTRA_PEOPLE)
                val telUri = people?.firstOrNull { it.startsWith("tel:") }
                if (telUri != null) {
                    "${sbn.packageName}_${telUri}_${sbn.postTime}"
                } else {
                    "${sbn.key}_${sbn.postTime}"
                }
            }
            else -> sbn.key
        }
    }

    // ---- Main Entry Point ----

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!managedApps.contains(sbn.packageName)) return
        if (sbn.postTime < listenerConnectedTime) return
        if (isSummaryNotification(sbn)) return

        val senderCooldownKey = "${sbn.packageName}_${getNotificationIdentifier(sbn)}"
        val now = System.currentTimeMillis()
        synchronized(this) {
            val lastPlayed = lastPlayedBySender[senderCooldownKey] ?: 0L
            if (now - lastPlayed < SENDER_COOLDOWN_MS) return
            lastPlayedBySender[senderCooldownKey] = now
        }

        suppressChannelSound()
        autoVerifySetup(sbn)

        val identifier = getNotificationIdentifier(sbn)
        val notifKey = buildNotifKey(sbn)

        synchronized(this) {
            if (playedKeys.contains(notifKey)) {
                Log.d("NotifySound", "BLOCKED by playedKeys: $notifKey")
                return
            }
            playedKeys.add(notifKey)
            keyMapping[sbn.key] = notifKey
        }

        Log.d("NotifySound", "RECEIVED: ${sbn.packageName} | $identifier")

        serviceScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).contactDao()
            val identifiersForApp = dao.getIdentifiersForPackage(sbn.packageName)

            val matched = when (sbn.packageName) {
                "com.instagram.android" -> {
                    val senderId = getInstagramSenderId(sbn)
                    val title = sbn.notification.extras
                        .getString(Notification.EXTRA_TITLE) ?: ""

                    val byId = if (senderId.isNotEmpty()) {
                        identifiersForApp.find { it.instagramSenderId == senderId }
                    } else null

                    val byName = if (byId == null) {
                        identifiersForApp.find {
                            it.instagramSenderId.isEmpty() &&
                                    title.equals(it.identifier, ignoreCase = true)
                        }
                    } else null

                    if (byName != null && senderId.isNotEmpty()) {
                        dao.updateIdentifier(byName.copy(instagramSenderId = senderId))
                    }

                    byId ?: byName
                }

                "com.whatsapp",
                "org.telegram.messenger" -> {
                    identifiersForApp.find { rule ->
                        identifier == rule.identifier ||
                                (identifier.length >= 9 && rule.identifier.length >= 9 &&
                                        identifier.takeLast(9) == rule.identifier.takeLast(9))
                    }
                }

                else -> {
                    identifiersForApp.find {
                        identifier.equals(it.identifier, ignoreCase = true)
                    }
                }
            }

            if (matched != null) {
                Log.d("NotifySound", "MATCHED: ${matched.identifier} → ${matched.soundFileName}")
                playCustomSound(matched.soundFileName)
            } else {
                // For Telegram without tel: URI it's a group update notification
                // Don't play default sound — the real message notification follows with tel: URI
                val shouldPlayDefault = if (sbn.packageName == "org.telegram.messenger") {
                    val people = sbn.notification.extras.getStringArray(Notification.EXTRA_PEOPLE)
                    people?.any { it.startsWith("tel:") } == true
                } else {
                    true
                }

                if (shouldPlayDefault) {
                    Log.d("NotifySound", "No match → default sound")
                    playDefaultNotificationSound()
                } else {
                    Log.d("NotifySound", "Telegram group update — skipping default sound")
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(this) {
            val dedupeKey = keyMapping.remove(sbn.key)
            if (dedupeKey != null) {
                playedKeys.remove(dedupeKey)
            }
        }
    }
}