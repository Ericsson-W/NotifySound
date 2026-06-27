package com.example.notifysound

import android.app.Notification
import android.app.Person
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    companion object {
        var isConnected = false
    }

    private val defaultSoundMap = mapOf(
        "com.google.android.gm" to R.raw.fahh
    )

    data class SoundRule(
        val packageName: String,
        val identifierMatch: String,
        val soundResId: Int
    )

    private val rules = listOf(
        SoundRule("com.google.android.gm", "wongericsson@gmail.com", R.raw.fahh),
        SoundRule("com.google.android.gm", "wongericsson01@gmail.com", R.raw.bruh),
        SoundRule("com.google.android.gm", "ersw0202@gmail.com", R.raw.fornite),
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.d("NotifySound", "CONNECTED")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.d("NotifySound", "DISCONNECTED")
    }

    private fun getNotificationIdentifier(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""

        val peopleList = extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)
        val senderEmail = peopleList?.firstOrNull()?.uri
            ?.removePrefix("mailto:")
            ?: ""

        Log.d("NotifySound", "TITLE=$title | SENDER_EMAIL=$senderEmail")

        return if (senderEmail.isNotEmpty()) senderEmail else title
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.e("NotifySound", "RECEIVED: ${sbn.packageName}")

        val identifier = getNotificationIdentifier(sbn)

        val matchedRule = rules.find {
            it.packageName == sbn.packageName &&
                    identifier.equals(it.identifierMatch, ignoreCase = true)
        }

        val soundRes = matchedRule?.soundResId
            ?: defaultSoundMap[sbn.packageName]
            ?: return

        if (matchedRule != null) {
            Log.d("NotifySound", "MATCHED RULE: ${matchedRule.identifierMatch}")
        } else {
            Log.d("NotifySound", "No specific rule matched, using default sound")
        }

        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            val afd = applicationContext.resources.openRawResourceFd(soundRes)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            mp.setOnCompletionListener { it.release() }
            mp.prepare()
            mp.start()
            Log.d("NotifySound", "Playing sound for ${sbn.packageName}")
        } catch (e: Exception) {
            Log.e("NotifySound", "Sound failed: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d("NotifySound", "REMOVED: ${sbn.packageName}")
    }
}