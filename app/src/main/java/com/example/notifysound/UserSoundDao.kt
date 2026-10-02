package com.example.notifysound

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSoundDao {

    @Query("SELECT * FROM user_sounds ORDER BY addedAt DESC")
    fun getAllSoundsFlow(): Flow<List<UserSound>>

    @Query("SELECT * FROM user_sounds ORDER BY addedAt DESC")
    suspend fun getAllSounds(): List<UserSound>

    @Insert
    suspend fun insertSound(sound: UserSound)

    @Delete
    suspend fun deleteSound(sound: UserSound)

    @Query("SELECT * FROM user_sounds WHERE uri = :uri LIMIT 1")
    suspend fun getSoundByUri(uri: String): UserSound?
}