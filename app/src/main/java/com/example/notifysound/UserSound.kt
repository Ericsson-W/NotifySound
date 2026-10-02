package com.example.notifysound

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_sounds")
data class UserSound(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val uri: String,           // content:// URI
    val displayName: String,   // filename shown in UI
    val addedAt: Long = System.currentTimeMillis()
)