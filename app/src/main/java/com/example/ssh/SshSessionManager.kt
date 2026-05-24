package com.example.ssh

import android.util.Log
import com.example.data.model.SshHost
import com.example.data.model.SshKey
import com.example.security.CryptoUtils
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Vector

data class SftpFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val permissions: String,
    val modifiedTime: Long
)

data class SshSessionTab(
    val tabId: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val hostId: Int?,
    val hostName: String,
    val port: Int,
    val username: String,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val error: String? = null,
    val terminalBuffer: String = "",
    val isSftpMode: Boolean = false,
    val currentDirectory: String = "/",
    val remoteFiles: List<SftpFile> = emptyList(),
    val isSftpLoading: Boolean = false,
    val sftpError: String? = null
)

class SshSessionManager {

    private val _sessions = MutableStateFlow<List<SshSessionTab>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId = _activeTabId.asStateFlow()

    // Active JSch connection objects stored in-memory
    private val activeSessions = mutableMapOf<String, Session>()
    private val activeShells = mutableMapOf<String, ChannelShell>()
    private val activeShellInputStreams = mutableMapOf<String, InputStream>()
    private val activeShellOutputStreams = mutableMapOf<String, OutputStream>()
    private val sessionScopes = mutableMapOf<String, CoroutineScope>()

    private val jsch = JSch()

    init {
        // Suppress JSch log noise if possible
        JSch.setLogger(object : com.jcraft.jsch.Logger {
            override fun isEnabled(level: Int): Boolean = false
            override fun log(level: Int, message: String) {}
        })
    }

    /**
     * Connects to a Host profile in a multi-tab environment.
     */
    fun connectHost(host: SshHost, keyPhrase: String, customKey: SshKey? = null) {
        val tabId = java.util.UUID.randomUUID().toString()
        val label = "${host.label} (${host.username}@${host.host})"
        
        val newTab = SshSessionTab(
            tabId = tabId,
            label = label,
            hostId = host.id,
            hostName = host.host,
            port = host.port,
            username = host.username,
            isConnecting = true
        )

        _sessions.update { it + newTab }
        _activeTabId.value = tabId

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        sessionScopes[tabId] = scope

        scope.launch {
            try {
                updateTab(tabId) { it.copy(terminalBuffer = "Connecting to ${host.host}:${host.port}...\n") }
                
                // Retrieve decrypt password
                val decryptedPassword = if (host.authType == "PASSWORD" && !host.encryptedPassword.isNullOrEmpty()) {
                    CryptoUtils.decrypt(host.encryptedPassword, keyPhrase)
                } else null

                // Configure JSch Instance for this tab
                val customJsch = JSch()
                
                // Add SSH Key identity if key-based auth
                if (host.authType == "KEY" && customKey != null) {
                    val decryptedKeyBytes = CryptoUtils.decrypt(customKey.encryptedPrivateKey, keyPhrase)
                        ?.toByteArray(StandardCharsets.UTF_8)
                    
                    val decryptedPassphraseBytes = if (!customKey.encryptedPassphrase.isNullOrEmpty()) {
                        CryptoUtils.decrypt(customKey.encryptedPassphrase, keyPhrase)?.toByteArray(StandardCharsets.UTF_8)
                    } else null

                    if (decryptedKeyBytes != null) {
                        customJsch.addIdentity(
                            customKey.label,
                            decryptedKeyBytes,
                            null, // public key can be null
                            decryptedPassphraseBytes
                        )
                    } else {
                        throw Exception("Failed to decrypt SSH Private Key.")
                    }
                }

                val session = customJsch.getSession(host.username, host.host, host.port)
                if (decryptedPassword != null) {
                    session.setPassword(decryptedPassword)
                }

                session.setConfig("StrictHostKeyChecking", "no")
                session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password")
                session.timeout = 15000 // 15s connection timeout

                session.connect()
                activeSessions[tabId] = session

                // Create shell channel
                val shellChannel = session.openChannel("shell") as ChannelShell
                // Allocate dynamic pty characteristics for a comfortable responsive visual terminal terminal
                shellChannel.setPtyType("xterm")
                
                val inputStream = shellChannel.inputStream
                val outputStream = shellChannel.outputStream

                shellChannel.connect(10000)
                activeShells[tabId] = shellChannel
                activeShellInputStreams[tabId] = inputStream
                activeShellOutputStreams[tabId] = outputStream

                updateTab(tabId) { 
                    it.copy(
                        isConnecting = false,
                        isConnected = true,
                        terminalBuffer = it.terminalBuffer + "Terminal Connected successfully.\n\n"
                    )
                }

                // Continuous Input Stream Reader Loop
                val buffer = ByteArray(2048)
                var bytesRead: Int
                while (shellChannel.isConnected) {
                    if (inputStream.available() > 0) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead > 0) {
                            val chunk = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
                            updateTab(tabId) { current ->
                                // Maintain a smooth visual limit for terminal command history to prevent memory leak
                                val cappedBuffer = if (current.terminalBuffer.length > 50000) {
                                    current.terminalBuffer.takeLast(30000)
                                } else current.terminalBuffer
                                val processedBuffer = TerminalBufferProcessor.processChunk(cappedBuffer, chunk)
                                current.copy(terminalBuffer = processedBuffer)
                            }
                        }
                    } else {
                        delay(50) // Non-blocking thread sleep to safeguard CPU usage cycles
                    }
                }

                // If loop exits, connection has been dropped
                disconnectTab(tabId)

            } catch (e: Exception) {
                e.printStackTrace()
                updateTab(tabId) { 
                    it.copy(
                        isConnecting = false,
                        isConnected = false,
                        error = e.localizedMessage ?: "Connection error",
                        terminalBuffer = it.terminalBuffer + "\n[System Status: Disconnected: ${e.localizedMessage}]\n"
                    )
                }
            }
        }
    }

    /**
     * Send command characters or inputs directly to the SSH shell
     */
    fun sendTerminalInput(tabId: String, text: String) {
        val outputStream = activeShellOutputStreams[tabId] ?: return
        val scope = sessionScopes[tabId] ?: return
        scope.launch(Dispatchers.IO) {
            try {
                outputStream.write(text.toByteArray(StandardCharsets.UTF_8))
                outputStream.flush()
            } catch (e: Exception) {
                Log.e("TitanSSH", "Failed to write terminal input: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Disconnects and wraps up a single tab session.
     */
    fun disconnectTab(tabId: String) {
        try {
            activeShells[tabId]?.disconnect()
            activeSessions[tabId]?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeShells.remove(tabId)
        activeShellInputStreams.remove(tabId)
        activeShellOutputStreams.remove(tabId)
        activeSessions.remove(tabId)
        
        sessionScopes[tabId]?.cancel()
        sessionScopes.remove(tabId)

        updateTab(tabId) { it.copy(isConnected = false, isConnecting = false) }
    }

    /**
     * Closes the tab completely, and selects a new active tab if required.
     */
    fun closeTab(tabId: String) {
        disconnectTab(tabId)
        _sessions.update { current ->
            val updated = current.filter { it.tabId != tabId }
            if (_activeTabId.value == tabId) {
                _activeTabId.value = updated.lastOrNull()?.tabId
            }
            updated
        }
    }

    fun selectTab(tabId: String) {
        _activeTabId.value = tabId
    }

    // --- SFTP VISUAL CHANNELS & OPERATIONS ---

    fun enterSftpMode(tabId: String, initialPath: String = "/") {
        updateTab(tabId) { it.copy(isSftpMode = true, currentDirectory = initialPath, sftpError = null) }
        loadSftpDirectory(tabId, initialPath)
    }

    fun exitSftpMode(tabId: String) {
        updateTab(tabId) { it.copy(isSftpMode = false) }
    }

    fun loadSftpDirectory(tabId: String, path: String) {
        val session = activeSessions[tabId]
        if (session == null || !session.isConnected) {
            updateTab(tabId) { it.copy(sftpError = "SSH host is currently offline.") }
            return
        }

        updateTab(tabId) { it.copy(isSftpLoading = true, currentDirectory = path, sftpError = null) }

        val scope = sessionScopes[tabId] ?: return
        scope.launch(Dispatchers.IO) {
            var sftpChannel: ChannelSftp? = null
            try {
                sftpChannel = session.openChannel("sftp") as ChannelSftp
                sftpChannel.connect(10000)

                sftpChannel.cd(path)
                val pwd = sftpChannel.pwd() // Correctly formatted path
                
                @Suppress("UNCHECKED_CAST")
                val filesVector = sftpChannel.ls(pwd) as Vector<ChannelSftp.LsEntry>
                val mappedFiles = filesVector.mapNotNull { entry ->
                    val name = entry.filename
                    if (name == "." || name == "..") return@mapNotNull null
                    
                    val attrs = entry.attrs
                    val fullPath = if (pwd.endsWith("/")) "$pwd$name" else "$pwd/$name"
                    SftpFile(
                        name = name,
                        path = fullPath,
                        isDirectory = attrs.isDir,
                        size = attrs.size,
                        permissions = attrs.permissionsString,
                        modifiedTime = attrs.mTime.toLong() * 1000L
                    )
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

                updateTab(tabId) {
                    it.copy(
                        isSftpLoading = false,
                        currentDirectory = pwd,
                        remoteFiles = mappedFiles,
                        sftpError = null
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                updateTab(tabId) {
                    it.copy(
                        isSftpLoading = false,
                        sftpError = "SFTP failure: ${e.localizedMessage}"
                    )
                }
            } finally {
                sftpChannel?.disconnect()
            }
        }
    }

    fun deleteSftpFile(tabId: String, file: SftpFile) {
        val session = activeSessions[tabId] ?: return
        val scope = sessionScopes[tabId] ?: return
        updateTab(tabId) { it.copy(isSftpLoading = true) }

        scope.launch(Dispatchers.IO) {
            var sftpChannel: ChannelSftp? = null
            try {
                sftpChannel = session.openChannel("sftp") as ChannelSftp
                sftpChannel.connect(5000)

                if (file.isDirectory) {
                    sftpChannel.rmdir(file.path)
                } else {
                    sftpChannel.rm(file.path)
                }
                
                // Reload current directory
                val currentDir = _sessions.value.firstOrNull { it.tabId == tabId }?.currentDirectory ?: "/"
                loadSftpDirectory(tabId, currentDir)
            } catch (e: Exception) {
                e.printStackTrace()
                updateTab(tabId) { it.copy(isSftpLoading = false, sftpError = "Delete file failed: ${e.localizedMessage}") }
            } finally {
                sftpChannel?.disconnect()
            }
        }
    }

    fun makeSftpDirectory(tabId: String, pathName: String) {
        val session = activeSessions[tabId] ?: return
        val scope = sessionScopes[tabId] ?: return
        val currentTab = _sessions.value.firstOrNull { it.tabId == tabId } ?: return
        updateTab(tabId) { it.copy(isSftpLoading = true) }

        scope.launch(Dispatchers.IO) {
            var sftpChannel: ChannelSftp? = null
            try {
                sftpChannel = session.openChannel("sftp") as ChannelSftp
                sftpChannel.connect(5000)

                val fullNewPath = if (currentTab.currentDirectory.endsWith("/")) {
                    "${currentTab.currentDirectory}$pathName"
                } else {
                    "${currentTab.currentDirectory}/$pathName"
                }

                sftpChannel.mkdir(fullNewPath)
                loadSftpDirectory(tabId, currentTab.currentDirectory)
            } catch (e: Exception) {
                e.printStackTrace()
                updateTab(tabId) { it.copy(isSftpLoading = false, sftpError = "Mkdir failed: ${e.localizedMessage}") }
            } finally {
                sftpChannel?.disconnect()
            }
        }
    }

    fun renameSftpFile(tabId: String, oldPath: String, newName: String) {
        val session = activeSessions[tabId] ?: return
        val scope = sessionScopes[tabId] ?: return
        val currentTab = _sessions.value.firstOrNull { it.tabId == tabId } ?: return
        updateTab(tabId) { it.copy(isSftpLoading = true) }

        scope.launch(Dispatchers.IO) {
            var sftpChannel: ChannelSftp? = null
            try {
                sftpChannel = session.openChannel("sftp") as ChannelSftp
                sftpChannel.connect(5000)

                val parentDir = oldPath.substringBeforeLast("/", "/")
                val newPath = if (parentDir.endsWith("/")) "$parentDir$newName" else "$parentDir/$newName"

                sftpChannel.rename(oldPath, newPath)
                loadSftpDirectory(tabId, currentTab.currentDirectory)
            } catch (e: Exception) {
                e.printStackTrace()
                updateTab(tabId) { it.copy(isSftpLoading = false, sftpError = "Rename failed: ${e.localizedMessage}") }
            } finally {
                sftpChannel?.disconnect()
            }
        }
    }

    // Helper to edit the state structure safely
    private fun updateTab(tabId: String, updateBlock: (SshSessionTab) -> SshSessionTab) {
        _sessions.update { current ->
            current.map { if (it.tabId == tabId) updateBlock(it) else it }
        }
    }
}
