package com.example.data.repository

import com.example.data.db.SshDao
import com.example.data.model.SshHost
import com.example.data.model.SshKey
import com.example.data.model.SshSnippet
import kotlinx.coroutines.flow.Flow

class SshRepository(private val sshDao: SshDao) {
    val allHosts: Flow<List<SshHost>> = sshDao.getAllHosts()
    val allKeys: Flow<List<SshKey>> = sshDao.getAllKeys()
    val allSnippets: Flow<List<SshSnippet>> = sshDao.getAllSnippets()

    suspend fun getHostById(id: Int): SshHost? {
        return sshDao.getHostById(id)
    }

    suspend fun saveHost(host: SshHost): Long {
        return sshDao.insertHost(host)
    }

    suspend fun deleteHost(host: SshHost) {
        sshDao.deleteHost(host)
    }

    suspend fun getKeyById(id: Int): SshKey? {
        return sshDao.getKeyById(id)
    }

    suspend fun saveKey(key: SshKey): Long {
        return sshDao.insertKey(key)
    }

    suspend fun deleteKey(key: SshKey) {
        sshDao.deleteKey(key)
    }

    suspend fun saveSnippet(snippet: SshSnippet): Long {
        return sshDao.insertSnippet(snippet)
    }

    suspend fun deleteSnippet(snippet: SshSnippet) {
        sshDao.deleteSnippet(snippet)
    }
}
