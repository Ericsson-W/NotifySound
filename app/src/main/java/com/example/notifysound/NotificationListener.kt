package com.example.notifysound

import android.app.Notification
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

class NotificationListener : NotificationListenerService() {

    companion object {
        var isConnected = false
        private val playedKeys = mutableSetOf<String>()
        private val keyMapping = mutableMapOf<String, String>()
        private var listenerConnectedTime: Long = 0
        private val lastPlayedBySender = mutableMapOf<String, Long>()
        private const val SENDER_COOLDOWN_MS = 500L
        private var currentPlayer: MediaPlayer? = null
    }

    private val managedApps = setOf(
        "com.google.android.gm",
        "com.instagram.android",
        "com.whatsapp"
    )

    private val summaryTitles = mapOf(
        "com.whatsapp" to setOf("WhatsApp"),
        "com.instagram.android" to setOf("Instagram")
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        listenerConnectedTime = System.currentTimeMillis()
        Log.d("NotifySound", "CONNECTED at $listenerConnectedTime")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.d("NotifySound", "DISCONNECTED")
    }

    private fun suppressChannelSound() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            Log.d("NotifySound", "Notification stream muted (was $originalVolume)")
            mainHandler.postDelayed({
                try {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_NOTIFICATION, originalVolume, 0
                    )
                    Log.d("NotifySound", "Notification stream restored to $originalVolume")
                } catch (e: Exception) {
                    Log.e("NotifySound", "Failed to restore volume: ${e.message}")
                }
            }, 2000)
        } catch (e: Exception) {
            Log.e("NotifySound", "suppressChannelSound failed: ${e.message}")
        }
    }

    private fun isSummaryNotification(sbn: StatusBarNotification): Boolean {
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val summaries = summaryTitles[sbn.packageName] ?: return false
        return title in summaries
    }

    private fun getGmailSenderEmail(extras: android.os.Bundle): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val people = extras.getParcelableArrayList<android.app.Person>(
                    Notification.EXTRA_PEOPLE_LIST
                )
                val uri = people?.firstOrNull()?.uri
                if (!uri.isNullOrEmpty()) {
                    return uri.removePrefix("mailto:")
                }
            } else {
                val people = extras.getParcelableArrayList<android.os.Parcelable>(
                    Notification.EXTRA_PEOPLE_LIST
                )
                people?.firstOrNull()?.let { person ->
                    val uri = person.javaClass.getMethod("getUri").invoke(person) as? String
                    if (!uri.isNullOrEmpty()) {
                        return uri.removePrefix("mailto:")
                    }
                }
            }
            ""
        } catch (e: Exception) {
            Log.e("NotifySound", "getGmailSenderEmail failed: ${e.message}")
            ""
        }
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
        } catch (e: Exception) {
            Log.e("NotifySound", "resolveWhatsAppPhoneNumber failed: ${e.message}")
            ""
        }
    }

    private fun getNotificationIdentifier(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""

        return when (sbn.packageName) {
            "com.google.android.gm" -> {
                val senderEmail = getGmailSenderEmail(extras)
                Log.d("NotifySound", "Gmail TITLE=$title | SENDER_EMAIL=$senderEmail")
                if (senderEmail.isNotEmpty()) senderEmail else title
            }

            "com.instagram.android" -> {
                val senderId = getInstagramSenderId(sbn)
                Log.d("NotifySound", "Instagram TITLE=$title | SENDER_ID=$senderId")
                title
            }

            "com.whatsapp" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val peopleList = extras.getParcelableArrayList<android.app.Person>(
                        Notification.EXTRA_PEOPLE_LIST
                    )
                    val contactUri = peopleList?.firstOrNull()?.uri ?: ""
                    if (contactUri.isNotEmpty() && contactUri.startsWith("content://")) {
                        val phoneNumber = resolveWhatsAppPhoneNumber(contactUri)
                        if (phoneNumber.isNotEmpty()) {
                            Log.d("NotifySound", "WhatsApp TITLE=$title | PHONE=$phoneNumber")
                            return phoneNumber
                        }
                    }
                }
                Log.d("NotifySound", "WhatsApp TITLE=$title | fallback to name")
                title
            }

            else -> {
                Log.d("NotifySound", "TITLE=$title")
                title
            }
        }
    }

    private fun buildNotifKey(sbn: StatusBarNotification): String {
        return when (sbn.packageName) {
            "com.instagram.android" -> {
                "${sbn.key}_${sbn.notification.`when`}"
            }
            "com.google.android.gm" -> {
                val extras = sbn.notification.extras
                val senderEmail = getGmailSenderEmail(extras)
                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val identifier = if (senderEmail.isNotEmpty()) senderEmail else title
                "${sbn.packageName}_$identifier"
            }
            "com.whatsapp" -> {
                "${sbn.key}_${sbn.notification.`when`}"
            }
            else -> sbn.key
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

    private fun playCustomSound(soundFileName: String) {
        val soundRes = resolveSoundFileName(soundFileName)
        try {
            // Stop any currently playing sound
            currentPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
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
                mp.setDataSource(
                    applicationContext,
                    android.net.Uri.parse(soundFileName)
                )
            } else {
                val afd = applicationContext.resources.openRawResourceFd(soundRes)
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }

            mp.setOnCompletionListener {
                it.release()
                currentPlayer = null
            }
            mp.prepare()
            mp.start()
            currentPlayer = mp
            Log.d("NotifySound", "Playing custom sound: $soundFileName")
        } catch (e: Exception) {
            Log.e("NotifySound", "Custom sound failed: ${e.message}")
        }
    }

    private fun playDefaultNotificationSound() {
        try {
            // Stop any currently playing sound
            currentPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
                currentPlayer = null
            }

            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, defaultUri)
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
            Log.d("NotifySound", "Playing system default notification sound")
        } catch (e: Exception) {
            Log.e("NotifySound", "Default sound failed: ${e.message}")
        }
    }

    private fun autoVerifySetup(sbn: StatusBarNotification) {
        val sound = sbn.notification.sound
        val defaults = sbn.notification.defaults
        val hasDefaultSound = (defaults and Notification.DEFAULT_SOUND) != 0
        val isSilent = sound == null && !hasDefaultSound

        Log.d("NotifySound", "Auto-verify ${sbn.packageName}: silent=$isSilent sound=$sound defaults=$defaults")

        if (sbn.packageName == "com.google.android.gm" && isSilent) {
            val prefs = applicationContext.getSharedPreferences(
                "notifysound_setup",
                android.content.Context.MODE_PRIVATE
            )
            if (!prefs.getBoolean(sbn.packageName, false)) {
                prefs.edit().putBoolean(sbn.packageName, true).apply()
                Log.d("NotifySound", "Auto-marked ${sbn.packageName} as configured ✅")
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.e("NotifySound", "RECEIVED: ${sbn.packageName}")

        if (!managedApps.contains(sbn.packageName)) {
            Log.d("NotifySound", "Unmanaged app — leaving untouched")
            return
        }

        if (sbn.postTime < listenerConnectedTime) {
            Log.d("NotifySound", "Ignoring old notification (pre-connection)")
            return
        }

        if (isSummaryNotification(sbn)) {
            Log.d("NotifySound", "Ignoring summary notification for ${sbn.packageName}")
            return
        }

        // Sender cooldown — catches simultaneous duplicate notifications
        val senderCooldownKey = "${sbn.packageName}_${getNotificationIdentifier(sbn)}"
        val now = System.currentTimeMillis()
        synchronized(this) {
            val lastPlayed = lastPlayedBySender[senderCooldownKey] ?: 0L
            if (now - lastPlayed < SENDER_COOLDOWN_MS) {
                Log.d("NotifySound", "Sender cooldown active for $senderCooldownKey — skipping")
                return
            }
            lastPlayedBySender[senderCooldownKey] = now
        }

        suppressChannelSound()
        autoVerifySetup(sbn)

        val identifier = getNotificationIdentifier(sbn)
        val notifKey = buildNotifKey(sbn)

        synchronized(this) {
            if (playedKeys.contains(notifKey)) {
                Log.d("NotifySound", "Already played sound for $notifKey — skipping")
                return
            }
            playedKeys.add(notifKey)
            keyMapping[sbn.key] = notifKey
        }

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
                        Log.d("NotifySound", "Locked sender_id $senderId for ${byName.identifier}")
                    }

                    byId ?: byName
                }

                "com.whatsapp" -> {
                    identifiersForApp.find { rule ->
                        identifier.equals(rule.identifier, ignoreCase = true) ||
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
                Log.d("NotifySound", "No match → playing system default")
                playDefaultNotificationSound()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(this) {
            val dedupeKey = keyMapping.remove(sbn.key)
            if (dedupeKey != null) {
                playedKeys.remove(dedupeKey)
                Log.d("NotifySound", "REMOVED: cleared dedupeKey $dedupeKey")
            } else {
                Log.d("NotifySound", "REMOVED: no mapping found for ${sbn.key}")
            }
        }
    }
}