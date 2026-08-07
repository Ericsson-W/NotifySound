package com.example.notifysound

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundRuleDao {

    @Query("SELECT * FROM sound_rules")
    fun getAllRulesFlow(): Flow<List<SoundRule>>

    @Query("SELECT * FROM sound_rules")
    suspend fun getAllRules(): List<SoundRule>

    @Insert
    suspend fun insertRule(rule: SoundRule)

    @Delete
    suspend fun deleteRule(rule: SoundRule)

    @Query("SELECT * FROM sound_rules WHERE packageName = :packageName")
    suspend fun getRulesForPackage(packageName: String): List<SoundRule>
}