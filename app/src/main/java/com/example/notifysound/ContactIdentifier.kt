package com.example.notifysound

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "contact_identifiers",
    foreignKeys = [ForeignKey(
        entity = Contact::class,
        parentColumns = ["id"],
        childColumns = ["contactId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ContactIdentifier(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val contactId: Int,
    val packageName: String,
    val identifier: String,
    val soundFileName: String,
    val instagramSenderId: String = "",
    val displayLabel: String = ""
)