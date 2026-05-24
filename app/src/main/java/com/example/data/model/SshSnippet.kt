package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_snippets")
data class SshSnippet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val commandText: String
)
