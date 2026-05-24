package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_keys")
data class SshKey(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val encryptedPrivateKey: String,
    val publicKey: String? = null,
    val encryptedPassphrase: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
