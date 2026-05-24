package com.example.data.db

import androidx.room.*
import com.example.data.model.SshHost
import com.example.data.model.SshKey
import com.example.data.model.SshSnippet
import kotlinx.coroutines.flow.Flow

@Dao
interface SshDao {
    // --- HOSTS ---
    @Query("SELECT * FROM ssh_hosts ORDER BY label ASC")
    fun getAllHosts(): Flow<List<SshHost>>

    @Query("SELECT * FROM ssh_hosts WHERE id = :id")
    suspend fun getHostById(id: Int): SshHost?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHost(host: SshHost): Long

    @Delete
    suspend fun deleteHost(host: SshHost)

    // --- KEYS ---
    @Query("SELECT * FROM ssh_keys ORDER BY label ASC")
    fun getAllKeys(): Flow<List<SshKey>>

    @Query("SELECT * FROM ssh_keys WHERE id = :id")
    suspend fun getKeyById(id: Int): SshKey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: SshKey): Long

    @Delete
    suspend fun deleteKey(key: SshKey)

    // --- SNIPPETS ---
    @Query("SELECT * FROM ssh_snippets ORDER BY label ASC")
    fun getAllSnippets(): Flow<List<SshSnippet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: SshSnippet): Long

    @Delete
    suspend fun deleteSnippet(snippet: SshSnippet)
}
