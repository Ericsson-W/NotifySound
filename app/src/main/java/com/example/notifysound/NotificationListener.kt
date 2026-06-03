package com.example.notifysound

import android.content.Context
import android.content.ComponentName
import android.media.MediaPlayer
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    companion object {
        var isConnected = false
    }

    private val soundMap = mapOf(
        "com.google.android.gm" to R.raw.fahh
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

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.e("NotifySound", "RECEIVED: ${sbn.packageName}")

        val soundRes = soundMap[sbn.packageName] ?: return

        cancelNotification(sbn.key)

        try {
            val mp = MediaPlayer()
            mp.setAudioStreamType(android.media.AudioManager.STREAM_NOTIFICATION)

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