package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_hosts")
data class SshHost(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: String = "PASSWORD", // "PASSWORD" or "KEY"
    val encryptedPassword: String? = null,
    val keyId: Int? = null, // Links to SshKey.id
    val groupName: String = "Servers", // Custom group/folder grouping
    val createdAt: Long = System.currentTimeMillis()
)
