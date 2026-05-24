package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.SshHost
import com.example.data.model.SshKey
import com.example.data.model.SshSnippet

@Database(
    entities = [SshHost::class, SshKey::class, SshSnippet::class],
    version = 1,
    exportSchema = false
)
abstract class SshDatabase : RoomDatabase() {
    abstract fun sshDao(): SshDao

    companion object {
        @Volatile
        private var INSTANCE: SshDatabase? = null

        fun getDatabase(context: Context): SshDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SshDatabase::class.java,
                    "titan_ssh_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
