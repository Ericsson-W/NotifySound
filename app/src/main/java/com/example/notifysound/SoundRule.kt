package com.example.notifysound

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sound_rules")
data class SoundRule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val packageName: String,       // e.g. "com.google.android.gm"
    val identifierMatch: String,   // e.g. "boss@example.com"
    val soundFileName: String      // e.g. "fahh" (we'll resolve this to R.raw dynamically)
)