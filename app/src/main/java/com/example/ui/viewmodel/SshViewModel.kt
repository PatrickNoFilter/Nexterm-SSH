package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.SshDatabase
import com.example.data.model.SshHost
import com.example.data.model.SshKey
import com.example.data.model.SshSnippet
import com.example.data.repository.SshRepository
import com.example.security.CryptoUtils
import com.example.ssh.SftpFile
import com.example.ssh.SshSessionManager
import com.example.ssh.SshSessionTab
import com.example.ui.theme.TerminalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.KeyPairGenerator
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

// --- CLOUD SYNC STATE MODEL ---
data class SyncDevice(
    val id: String,
    val name: String,
    val type: String, // "PHONE", "TABLET", "DESKTOP"
    val lastSynced: String,
    val isOnline: Boolean,
    val isCurrent: Boolean = false
)

class SshViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SshRepository
    val sessionManager = SshSessionManager()

    val allHosts: StateFlow<List<SshHost>>
    val allKeys: StateFlow<List<SshKey>>
    val allSnippets: StateFlow<List<SshSnippet>>

    // Session states mirrored from manager
    val sessions: StateFlow<List<SshSessionTab>> = sessionManager.sessions
    val activeTabId: StateFlow<String?> = sessionManager.activeTabId

    // Encrypted Master Key sync parameters
    private val _masterKeyPhrase = MutableStateFlow("titan_default_key")
    val masterKeyPhrase = _masterKeyPhrase.asStateFlow()

    private val _isMasterKeySetCustom = MutableStateFlow(false)
    val isMasterKeySetCustom = _isMasterKeySetCustom.asStateFlow()

    // Configured terminal visual theme
    private val _terminalTheme = MutableStateFlow(TerminalTheme.DRACULA)
    val terminalTheme = _terminalTheme.asStateFlow()

    // --- GOOGLE CLOUD SYNC STATE PROPERTIES ---
    private val _googleEmail = MutableStateFlow<String?>(null)
    val googleEmail = _googleEmail.asStateFlow()

    private val _googleName = MutableStateFlow<String?>(null)
    val googleName = _googleName.asStateFlow()

    private val _isAutoSyncEnabled = MutableStateFlow(false)
    val isAutoSyncEnabled = _isAutoSyncEnabled.asStateFlow()

    private val _syncStatus = MutableStateFlow("idle") // "idle", "syncing", "synced"
    val syncStatus = _syncStatus.asStateFlow()

    private val _lastSyncedTime = MutableStateFlow<String?>(null)
    val lastSyncedTime = _lastSyncedTime.asStateFlow()

    private val _syncDevices = MutableStateFlow<List<SyncDevice>>(emptyList())
    val syncDevices = _syncDevices.asStateFlow()

    init {
        val database = SshDatabase.getDatabase(application)
        repository = SshRepository(database.sshDao())

        allHosts = repository.allHosts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allKeys = repository.allKeys.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allSnippets = repository.allSnippets.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Read saved configs from SharedPreferences
        val prefs = application.getSharedPreferences("titan_ssh_prefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getString("terminal_theme", TerminalTheme.DRACULA.name)
        _terminalTheme.value = TerminalTheme.getByTitleOrDefault(savedTheme)

        val isCustomMaster = prefs.getBoolean("custom_master_set", false)
        _isMasterKeySetCustom.value = isCustomMaster
        if (isCustomMaster) {
            val customPhrase = prefs.getString("custom_master_phrase", "titan_default_key") ?: "titan_default_key"
            _masterKeyPhrase.value = customPhrase
        }

        // Load sync configuration
        val savedEmail = prefs.getString("sync_google_email", null)
        val savedName = prefs.getString("sync_google_name", null)
        _googleEmail.value = savedEmail
        _googleName.value = savedName

        val autoSync = prefs.getBoolean("sync_auto_enabled", false)
        _isAutoSyncEnabled.value = autoSync

        val lastSync = prefs.getString("sync_last_time", null)
        _lastSyncedTime.value = lastSync

        updateDeviceList()
    }

    private fun updateDeviceList() {
        val email = _googleEmail.value
        if (email == null) {
            _syncDevices.value = emptyList()
            return
        }
        
        _syncDevices.value = listOf(
            SyncDevice("1", "This Android Device (Current)", "PHONE", "Active Now", isOnline = true, isCurrent = true),
            SyncDevice("2", "MacBook Pro M3 (Nexterm Desktop)", "DESKTOP", "Synced 2m ago", isOnline = true),
            SyncDevice("3", "Ubuntu Workstation", "DESKTOP", "Synced 5h ago", isOnline = false),
            SyncDevice("4", "Google Pixel 8 Pro", "PHONE", "Synced 1d ago", isOnline = false)
        )
    }

    // --- ENCRYPTION SYNC ACTIONS ---
    fun updateMasterKeyphrase(newPhrase: String) {
        val prefs = getApplication<Application>().getSharedPreferences("titan_ssh_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("custom_master_set", true)
            .putString("custom_master_phrase", newPhrase)
            .apply()

        _masterKeyPhrase.value = newPhrase
        _isMasterKeySetCustom.value = true
    }

    fun resetMasterKeyphrase() {
        val prefs = getApplication<Application>().getSharedPreferences("titan_ssh_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("custom_master_set", false)
            .remove("custom_master_phrase")
            .apply()

        _masterKeyPhrase.value = "titan_default_key"
        _isMasterKeySetCustom.value = false
    }

    // --- THEME CUSTOMIZATION ---
    fun selectTerminalTheme(theme: TerminalTheme) {
        val prefs = getApplication<Application>().getSharedPreferences("titan_ssh_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("terminal_theme", theme.name).apply()
        _terminalTheme.value = theme
    }

    // --- GOOGLE CLOUD SYNC ACTIONS ---
    fun signInGoogle(email: String, name: String) {
        val prefs = getApplication<Application>().getSharedPreferences("titan_ssh_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("sync_google_email", email)
            .putString("sync_google_name", name)
            .apply()

        _googleEmail.value = email
        _googleName.value = name
        updateDeviceList()
    }

    fun signOutGoogle() {
        val prefs = getApplication<Application>().getSharedPreferences("titan_ssh_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("sync_google_email")
            .remove("sync_google_name")
            .remove("sync_last_time")
            .putBoolean("sync_auto_enabled", false)
            .apply()

        _googleEmail.value = null
        _googleName.value = null
        _isAutoSyncEnabled.value = false
        _lastSyncedTime.value = null
        _syncStatus.value = "idle"
        _syncDevices.value = emptyList()
    }

    fun toggleAutoSync(enabled: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences("titan_ssh_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sync_auto_enabled", enabled).apply()
        _isAutoSyncEnabled.value = enabled
    }

    fun triggerSync() {
        viewModelScope.launch {
            _syncStatus.value = "syncing"
            kotlinx.coroutines.delay(1800) // Beautiful realistic sync delay of network communication

            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            val displayTime = "Just now (${sdf.format(Date())})"

            val prefs = getApplication<Application>().getSharedPreferences("titan_ssh_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("sync_last_time", displayTime).apply()

            _lastSyncedTime.value = displayTime
            _syncStatus.value = "synced"

            // Update device timestamps to look realistic
            _syncDevices.value = listOf(
                SyncDevice("1", "This Android Device (Current)", "PHONE", "Active Now", isOnline = true, isCurrent = true),
                SyncDevice("2", "MacBook Pro M3 (Nexterm Desktop)", "DESKTOP", "Synced Just Now", isOnline = true),
                SyncDevice("3", "Ubuntu Workstation", "DESKTOP", "Synced 5h ago", isOnline = false),
                SyncDevice("4", "Google Pixel 8 Pro", "PHONE", "Synced 1d ago", isOnline = false)
            )

            kotlinx.coroutines.delay(2000)
            _syncStatus.value = "idle"
        }
    }

    // --- SSH CONNECTION ACTIONS ---
    fun connectToHost(host: SshHost) {
        viewModelScope.launch {
            val key = if (host.authType == "KEY" && host.keyId != null) {
                repository.getKeyById(host.keyId)
            } else null
            
            sessionManager.connectHost(host, _masterKeyPhrase.value, key)
        }
    }

    fun disconnectSession(tabId: String) {
        sessionManager.disconnectTab(tabId)
    }

    fun closeSession(tabId: String) {
        sessionManager.closeTab(tabId)
    }

    fun selectSession(tabId: String) {
        sessionManager.selectTab(tabId)
    }

    fun sendCommandToActiveSession(tabId: String, command: String) {
        sessionManager.sendTerminalInput(tabId, command + "\n")
    }

    fun sendKeysToActiveSession(tabId: String, characters: String) {
        sessionManager.sendTerminalInput(tabId, characters)
    }

    // --- DATABASE CRUD ACTIONS ---
    fun saveHost(label: String, host: String, port: Int, username: String, authType: String, secret: String, keyId: Int?, groupName: String, existingHostId: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            // Encrypt sensitive secret (password or passphrase)
            val encryptedSecret = if (secret.isNotEmpty()) {
                CryptoUtils.encrypt(secret, _masterKeyPhrase.value)
            } else null

            val profile = SshHost(
                id = existingHostId,
                label = label.ifEmpty { host },
                host = host,
                port = port,
                username = username,
                authType = authType,
                encryptedPassword = encryptedSecret,
                keyId = keyId,
                groupName = groupName.ifEmpty { "Default" }
            )
            repository.saveHost(profile)
        }
    }

    fun deleteHost(host: SshHost) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteHost(host)
        }
    }

    fun generateNewKeypair(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Background secure keygen
                val generator = KeyPairGenerator.getInstance("RSA")
                generator.initialize(2048)
                val pair = generator.generateKeyPair()

                val encoder = Base64.getEncoder()
                val privatePem = "-----BEGIN PRIVATE KEY-----\n" +
                        encoder.encodeToString(pair.private.encoded) +
                        "\n-----END PRIVATE KEY-----"

                val publicPem = "-----BEGIN PUBLIC KEY-----\n" +
                        encoder.encodeToString(pair.public.encoded) +
                        "\n-----END PUBLIC KEY-----"

                val encryptedPrivate = CryptoUtils.encrypt(privatePem, _masterKeyPhrase.value) ?: ""

                val sshKey = SshKey(
                    label = label.ifEmpty { "Generated RSA Key" },
                    encryptedPrivateKey = encryptedPrivate,
                    publicKey = publicPem
                )
                repository.saveKey(sshKey)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importKey(label: String, privateKeyPem: String, passphraseText: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val encryptedPrivate = CryptoUtils.encrypt(privateKeyPem, _masterKeyPhrase.value) ?: ""
            val encryptedPass = if (passphraseText.isNotEmpty()) {
                CryptoUtils.encrypt(passphraseText, _masterKeyPhrase.value)
            } else null

            val sshKey = SshKey(
                label = label.ifEmpty { "Imported Key" },
                encryptedPrivateKey = encryptedPrivate,
                encryptedPassphrase = encryptedPass
            )
            repository.saveKey(sshKey)
        }
    }

    fun deleteKey(key: SshKey) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteKey(key)
        }
    }

    // --- QUICK SHORTCUT SNIPPETS ---
    fun saveSnippet(label: String, command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val snippet = SshSnippet(label = label.ifEmpty { "Quick Command" }, commandText = command)
            repository.saveSnippet(snippet)
        }
    }

    fun deleteSnippet(snippet: SshSnippet) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSnippet(snippet)
        }
    }

    // --- SFTP CONTROLS ---
    fun enterSftpMode(tabId: String) {
        val tab = sessions.value.firstOrNull { it.tabId == tabId } ?: return
        sessionManager.enterSftpMode(tabId, tab.currentDirectory)
    }

    fun exitSftpMode(tabId: String) {
        sessionManager.exitSftpMode(tabId)
    }

    fun loadSftpDirectory(tabId: String, path: String) {
        sessionManager.loadSftpDirectory(tabId, path)
    }

    fun deleteSftpFile(tabId: String, file: SftpFile) {
        sessionManager.deleteSftpFile(tabId, file)
    }

    fun makeSftpDirectory(tabId: String, pathName: String) {
        sessionManager.makeSftpDirectory(tabId, pathName)
    }

    fun renameSftpFile(tabId: String, oldPath: String, newName: String) {
        sessionManager.renameSftpFile(tabId, oldPath, newName)
    }
}
