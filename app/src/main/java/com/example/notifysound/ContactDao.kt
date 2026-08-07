package com.example.notifysound

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Update
    suspend fun updateContact(contact: Contact)
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContactsFlow(): Flow<List<Contact>>

    @Insert
    suspend fun insertContact(contact: Contact): Long

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Insert
    suspend fun insertIdentifier(identifier: ContactIdentifier)

    @Delete
    suspend fun deleteIdentifier(identifier: ContactIdentifier)

    @Update
    suspend fun updateIdentifier(identifier: ContactIdentifier)

    @Query("SELECT * FROM contact_identifiers WHERE contactId = :contactId")
    fun getIdentifiersForContact(contactId: Int): Flow<List<ContactIdentifier>>

    @Query("SELECT * FROM contact_identifiers WHERE packageName = :packageName")
    suspend fun getIdentifiersForPackage(packageName: String): List<ContactIdentifier>

    @Query("SELECT * FROM contact_identifiers")
    suspend fun getAllIdentifiers(): List<ContactIdentifier>
}