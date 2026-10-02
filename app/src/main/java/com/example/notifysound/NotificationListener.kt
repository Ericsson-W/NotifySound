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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        private var savedNotificationVolume: Int = -1
    }

    private val managedApps = setOf(
        "com.google.android.gm",
        "com.instagram.android",
        "com.whatsapp",
        "org.telegram.messenger"
    )

    private val summaryTitles = mapOf(
        "com.whatsapp" to setOf("WhatsApp"),
        "com.instagram.android" to setOf("Instagram", ""),
        "org.telegram.messenger" to setOf("Telegram")
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val restoreVolumeRunnable = Runnable { restoreNotificationVolume() }

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
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("NotifySound is active")
            .setContentText("Listening for notifications")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setAutoCancel(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ---- Lifecycle ----

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        listenerConnectedTime = System.currentTimeMillis()
        Log.d("NotifySound", "CONNECTED")
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_ID,
                buildForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_ID,
                buildForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
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

    // ---- Volume Management ----

    private fun suppressChannelSound() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            mainHandler.removeCallbacks(restoreVolumeRunnable)
            mainHandler.postDelayed(restoreVolumeRunnable, 3000)
        } catch (_: Exception) { }
    }

    private fun restoreNotificationVolume() {
        try {
            if (savedNotificationVolume >= 0) {
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(
                    AudioManager.STREAM_NOTIFICATION,
                    savedNotificationVolume,
                    0
                )
                savedNotificationVolume = -1
            }
        } catch (_: Exception) { }
    }

    // ---- Audio ----

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

    private fun playCustomSound(soundFileName: String) {
        try {
            currentPlayer?.let {
                try { if (it.isPlaying) it.stop(); it.release() } catch (_: Exception) { }
                currentPlayer = null
            }

            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            val volumeRatio = if (savedNotificationVolume >= 0 && maxVolume > 0)
                savedNotificationVolume.toFloat() / maxVolume.toFloat()
            else 1.0f

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

            mp.setOnCompletionListener {
                it.release()
                currentPlayer = null
            }
            mp.prepare()
            mp.setVolume(volumeRatio, volumeRatio)
            mp.start()
            currentPlayer = mp
            Log.d("NotifySound", "Playing: $soundFileName at volume $volumeRatio")
        } catch (e: Exception) {
            Log.e("NotifySound", "Sound failed: ${e.message}")
        }
    }

    private fun playDefaultNotificationSound() {
        try {
            currentPlayer?.let {
                try { if (it.isPlaying) it.stop(); it.release() } catch (_: Exception) { }
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

    // ---- Contact Lookups ----

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
        } catch (_: Exception) { "" }
    }

    private fun resolvePhoneFromDisplayName(displayName: String): String {
        return try {
            val cursor = applicationContext.contentResolver.query(
                android.provider.ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.Contacts._ID,
                    android.provider.ContactsContract.Contacts.DISPLAY_NAME
                ),
                "${android.provider.ContactsContract.Contacts.DISPLAY_NAME} = ?",
                arrayOf(displayName),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val contactId = it.getString(
                        it.getColumnIndexOrThrow(
                            android.provider.ContactsContract.Contacts._ID
                        )
                    )
                    val phoneCursor = applicationContext.contentResolver.query(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
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
        } catch (_: Exception) { "" }
    }

    private fun resolveNameFromPhone(phone: String): String {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phone)
            )
            val cursor = applicationContext.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) return it.getString(0) ?: ""
            }
            ""
        } catch (_: Exception) { "" }
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
        } catch (_: Exception) { "" }
    }

    private fun getInstagramSenderId(sbn: StatusBarNotification): String {
        return sbn.notification.extras.getString(
            "com.instagram.android.igns.logging.sender_id"
        ) ?: ""
    }

    private fun getNotificationIdentifier(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""

        return when (sbn.packageName) {
            "com.google.android.gm" -> {
                val senderEmail = getGmailSenderEmail(extras)
                if (senderEmail.isNotEmpty()) senderEmail else title
            }

            "com.instagram.android" -> {
                val senderId = getInstagramSenderId(sbn)
                val selfDisplayName = extras.getString("android.selfDisplayName") ?: ""
                val senderName = when {
                    selfDisplayName.isNotEmpty() &&
                            title.startsWith("$selfDisplayName: ") ->
                        title.removePrefix("$selfDisplayName: ")
                    title.contains(": ") &&
                            !extras.getBoolean("android.isGroupConversation", false) ->
                        title.substringAfter(": ")
                    else -> title
                }
                Log.d("NotifySound", "Instagram SENDER=$senderName SENDER_ID=$senderId")
                senderName
            }

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
                Log.d("NotifySound", "Telegram TITLE=$title")
                title
            }

            else -> title
        }
    }

    // ---- Notification Helpers ----

    private fun buildNotifKey(sbn: StatusBarNotification): String {
        return when (sbn.packageName) {
            "com.instagram.android" ->
                "${sbn.key}_${sbn.notification.`when`}"
            "com.google.android.gm" -> {
                val extras = sbn.notification.extras
                val senderEmail = getGmailSenderEmail(extras)
                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val identifier = if (senderEmail.isNotEmpty()) senderEmail else title
                "${sbn.packageName}_$identifier"
            }
            "com.whatsapp" ->
                "${sbn.key}_${sbn.notification.`when`}"
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

    private fun isSummaryNotification(sbn: StatusBarNotification): Boolean {
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val summaries = summaryTitles[sbn.packageName] ?: return false
        return title in summaries
    }

    private fun autoVerifySetup(sbn: StatusBarNotification) {
        @Suppress("DEPRECATION")
        val sound = sbn.notification.sound
        @Suppress("DEPRECATION")
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

    // ---- Main Entry Point ----

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!managedApps.contains(sbn.packageName)) return
        if (sbn.postTime < listenerConnectedTime) return
        if (isSummaryNotification(sbn)) return

        val identifier = getNotificationIdentifier(sbn)

        // Telegram: skip if identifier is just the group name with no sender
        if (sbn.packageName == "org.telegram.messenger") {
            val convTitle = sbn.notification.extras.getCharSequence(
                Notification.EXTRA_CONVERSATION_TITLE
            )?.toString() ?: ""
            if (convTitle.isNotEmpty() && identifier == convTitle) {
                Log.d("NotifySound", "Telegram: group summary — skipping")
                return
            }
        }

        // Skip empty identifier — summary/system notifications
        if (identifier.isEmpty()) {
            Log.d("NotifySound", "Empty identifier — skipping")
            return
        }

        // Sender cooldown
        val senderCooldownKey = "${sbn.packageName}_$identifier"
        val now = System.currentTimeMillis()
        synchronized(this) {
            val lastPlayed = lastPlayedBySender[senderCooldownKey] ?: 0L
            if (now - lastPlayed < SENDER_COOLDOWN_MS) return
            lastPlayedBySender[senderCooldownKey] = now
        }

        // Save volume before suppressing
        if (savedNotificationVolume < 0) {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            savedNotificationVolume = audioManager.getStreamVolume(
                AudioManager.STREAM_NOTIFICATION
            )
            Log.d("NotifySound", "Volume saved: $savedNotificationVolume")
        }

        suppressChannelSound()
        autoVerifySetup(sbn)

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

                    val byId = if (senderId.isNotEmpty()) {
                        identifiersForApp.find { it.instagramSenderId == senderId }
                    } else null

                    val byName = if (byId == null) {
                        identifiersForApp.find {
                            it.instagramSenderId.isEmpty() &&
                                    identifier.equals(it.identifier, ignoreCase = true)
                        }
                    } else null

                    if (byName != null && senderId.isNotEmpty()) {
                        dao.updateIdentifier(byName.copy(instagramSenderId = senderId))
                        Log.d("NotifySound", "Locked sender_id $senderId for ${byName.identifier}")
                    }

                    byId ?: byName
                }

                "com.whatsapp" -> {
                    identifiersForApp.find { rule ->
                        identifier == rule.identifier ||
                                (identifier.length >= 9 && rule.identifier.length >= 9 &&
                                        identifier.takeLast(9) == rule.identifier.takeLast(9))
                    }
                }

                "org.telegram.messenger" -> {
                    val convTitle = sbn.notification.extras.getCharSequence(
                        Notification.EXTRA_CONVERSATION_TITLE
                    )?.toString() ?: ""

                    val senderPortion = if (convTitle.isNotEmpty()) {
                        identifier.substringAfter("$convTitle: ").trim()
                    } else {
                        identifier
                    }

                    Log.d("NotifySound", "Telegram senderPortion=$senderPortion")

                    val result = identifiersForApp.find { rule ->
                        when {
                            // Path 1 — identifier is a phone number (emulator with tel: URI)
                            senderPortion.startsWith("+") -> {
                                senderPortion == rule.identifier ||
                                        (senderPortion.length >= 9 && rule.identifier.length >= 9 &&
                                                senderPortion.takeLast(9) == rule.identifier.takeLast(9))
                            }
                            // Path 2 — rule has phone, look up contact name and compare
                            rule.identifier.startsWith("+") -> {
                                val contactName = resolveNameFromPhone(rule.identifier)
                                Log.d("NotifySound", "Telegram contactName=$contactName")
                                contactName.isNotEmpty() &&
                                        senderPortion.startsWith(contactName, ignoreCase = true)
                            }
                            // Path 3 — both are display names, direct match
                            else -> {
                                senderPortion.equals(rule.identifier, ignoreCase = true) ||
                                        (!rule.displayLabel.isNullOrEmpty() &&
                                                senderPortion.equals(rule.displayLabel, ignoreCase = true))
                            }
                        }
                    }

                    // Lock in phone if matched by name
                    if (result != null && !result.identifier.startsWith("+") &&
                        !senderPortion.startsWith("+")) {
                        val phone = resolvePhoneFromDisplayName(senderPortion)
                        if (phone.isNotEmpty()) {
                            val currentName = resolveNameFromPhone(phone).ifEmpty { senderPortion }
                            dao.updateIdentifier(result.copy(
                                identifier = phone,
                                displayLabel = currentName
                            ))
                            Log.d("NotifySound", "Telegram: locked phone=$phone name=$currentName")
                        }
                    } else if (result != null && result.identifier.startsWith("+")) {
                        val currentName = resolveNameFromPhone(result.identifier).ifEmpty { senderPortion }
                        if (!currentName.equals(result.displayLabel, ignoreCase = true)) {
                            dao.updateIdentifier(result.copy(displayLabel = currentName))
                            Log.d("NotifySound", "Telegram: updated name to $currentName")
                        }
                    }

                    result
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
                Log.d("NotifySound", "No match → default sound")
                playDefaultNotificationSound()
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