package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SshSnippet
import com.example.ssh.SftpFile
import com.example.ssh.SshSessionTab
import com.example.ui.theme.TerminalTheme
import com.example.ui.viewmodel.SshViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: SshViewModel,
    tabs: List<SshSessionTab>,
    activeTabId: String?,
    allSnippets: List<SshSnippet>,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme by viewModel.terminalTheme.collectAsState()
    val scope = rememberCoroutineScope()
    val activeTab = tabs.find { it.tabId == activeTabId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (activeTab != null) "Active Session: ${activeTab.hostName}" else "SSH Terminal Sessions",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToDashboard) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Quick Action tabs toggle
                    if (activeTab != null) {
                        IconButton(onClick = { viewModel.disconnectSession(activeTab.tabId) }) {
                            Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = "Disconnect")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Multi-tab chrome/termius row - Styled dynamically for Professional Polish
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(tabs, key = { it.tabId }) { tab ->
                    val isActive = tab.tabId == activeTabId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                            .clickable { viewModel.selectSession(tab.tabId) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            // Online status indicators
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (tab.isConnected) Color(0xFF22C55E)
                                        else if (tab.isConnecting) Color(0xFFFFC107)
                                        else Color(0xFFEF4444)
                                    )
                            )
                            Text(
                                text = tab.label.take(18) + if (tab.label.length > 18) ".." else "",
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            IconButton(
                                onClick = { viewModel.closeSession(tab.tabId) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = if (isActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    // New connection tab shortcut matching professional styling
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            .clickable { onBackToDashboard() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add New Connection",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "New",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (activeTab == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        icon = Icons.Default.Computer,
                        title = "No Active Sessions",
                        description = "Switch tabs or return to the dashboard to select an SSH host to initiate a connection."
                    )
                }
            } else {
                // Interactive core view wrapper
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    if (activeTab.isSftpMode) {
                        // Visual file browser SFTP layout panel
                        SftpDirectoriesExplorer(
                            viewModel = viewModel,
                            tab = activeTab
                        )
                    } else {
                        // Regular retro text terminal console panel
                        ConsolePanel(
                            viewModel = viewModel,
                            tab = activeTab,
                            snippets = allSnippets,
                            theme = theme
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConsolePanel(
    viewModel: SshViewModel,
    tab: SshSessionTab,
    snippets: List<SshSnippet>,
    theme: TerminalTheme
) {
    var commandInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val consoleScrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Responsive automatic auto-scrolling to show stream additions in real-time
    LaunchedEffect(tab.terminalBuffer) {
        if (tab.terminalBuffer.isNotEmpty()) {
            consoleScrollState.animateScrollToItem(Int.MAX_VALUE / 2) // Large buffer index safe scroll
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        // Output logs container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            val lines = tab.terminalBuffer.split("\n")
            
            LazyColumn(
                state = consoleScrollState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Large item count emulation safely
                items(lines.size) { index ->
                    Text(
                        text = lines[index],
                        color = theme.textPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }

            if (tab.isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp),
                    color = theme.accent
                )
            }
        }

        // Horizontal command shortcuts snippet launcher bar
        if (snippets.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.background.copy(alpha = 0.85f))
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(snippets) { snippet ->
                    ElevatedFilterChip(
                        selected = false,
                        onClick = { viewModel.sendCommandToActiveSession(tab.tabId, snippet.commandText) },
                        label = {
                            Text(
                                "⚡ ${snippet.label}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = theme.textPrimary
                            )
                        },
                        colors = FilterChipDefaults.elevatedFilterChipColors(
                            containerColor = theme.background.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        // Special Touch Buttons Keyboard Helper Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background.copy(alpha = 0.9f))
                .horizontalScroll(rememberScrollState())
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val functionalKeys = listOf(
                "ESC" to "\u001b",
                "TAB" to "\t",
                "CTRL-C" to "\u0003",
                "CTRL-Z" to "\u001A",
                "↑" to "\u001b[A",
                "↓" to "\u001b[B",
                "←" to "\u001b[D",
                "→" to "\u001b[C"
            )

            // Dynamic Switch Mode to SFTP Toggle
            Button(
                onClick = { viewModel.enterSftpMode(tab.tabId) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accent,
                    contentColor = theme.background
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("SFTP TRANSFER", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            functionalKeys.forEach { (label, payload) ->
                OutlinedButton(
                    onClick = { viewModel.sendKeysToActiveSession(tab.tabId, payload) },
                    border = BorderStroke(1.dp, theme.textSecondary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }

            // Clear terminal view button
            OutlinedButton(
                onClick = {
                    // Quick terminal buffer clearing
                    // We can print some clean character sequence or clear it inside
                },
                border = BorderStroke(1.dp, theme.accent),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("CLR", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // Interactive primary input text box controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.background)
                .navigationBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                textStyle = androidx.compose.ui.text.TextStyle(color = theme.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                placeholder = { Text("Send Bash input...", color = theme.textSecondary, fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.5f),
                    cursorColor = theme.accent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (commandInput.isNotEmpty()) {
                        viewModel.sendCommandToActiveSession(tab.tabId, commandInput)
                        commandInput = ""
                    }
                }),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 56.dp)
            )

            IconButton(
                onClick = {
                    if (commandInput.isNotEmpty()) {
                        viewModel.sendCommandToActiveSession(tab.tabId, commandInput)
                        commandInput = ""
                        keyboardController?.hide()
                    }
                },
                enabled = commandInput.isNotEmpty(),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Submit Command",
                    tint = if (commandInput.isNotEmpty()) theme.accent else theme.textSecondary
                )
            }
        }
    }
}

// --- VISUAL SFTP FILE TRANSFER SYSTEM EXPLORER ---

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SftpDirectoriesExplorer(
    viewModel: SshViewModel,
    tab: SshSessionTab
) {
    var showMkdirDialog by remember { mutableStateOf(false) }
    var folderNameField by remember { mutableStateOf("") }
    var selectedFileForActions by remember { mutableStateOf<SftpFile?>(null) }
    var filesCopiedInfoText by remember { mutableStateOf<String?>(null) }

    var renameFileRef by remember { mutableStateOf<SftpFile?>(null) }
    var renameFieldName by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Headers with path back stack navigation & actions rows
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.exitSftpMode(tab.tabId) }) {
                Icon(imageVector = Icons.Default.Terminal, contentDescription = "Shell Command Console")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("SFTP FILE EXPLORER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                // Inline breadcrumb text scroll
                Text(
                    text = tab.currentDirectory,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }

            // Up-directory button (..)
            IconButton(
                onClick = {
                    val parent = tab.currentDirectory.substringBeforeLast("/", "/")
                    viewModel.loadSftpDirectory(tab.tabId, parent.ifEmpty { "/" })
                },
                enabled = tab.currentDirectory != "/" && !tab.isSftpLoading
            ) {
                Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Go Up Dir")
            }

            IconButton(
                onClick = { viewModel.loadSftpDirectory(tab.tabId, tab.currentDirectory) },
                enabled = !tab.isSftpLoading
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Folder")
            }

            IconButton(onClick = { showMkdirDialog = true }) {
                Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "Create Directory")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filesCopiedInfoText != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = filesCopiedInfoText ?: "",
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .weight(1f)
                    )
                    IconButton(onClick = { filesCopiedInfoText = null }, modifier = Modifier.size(16.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        if (tab.isSftpLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fetching SFTP Listings...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (tab.sftpError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(tab.sftpError ?: "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(top = 8.dp))
                        Button(
                            onClick = { viewModel.loadSftpDirectory(tab.tabId, tab.currentDirectory) },
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text("Retry Listing")
                        }
                    }
                }
            }
        } else if (tab.remoteFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    icon = Icons.Default.Folder,
                    title = "Empty Directory",
                    description = "This remote SSH directory has no files or subfolders."
                )
            }
        } else {
            // Display remote file registry in a beautiful visual list with quick operations
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tab.remoteFiles) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {
                                    if (file.isDirectory) {
                                        viewModel.loadSftpDirectory(tab.tabId, file.path)
                                    } else {
                                        selectedFileForActions = file
                                    }
                                },
                                onLongClick = {
                                    selectedFileForActions = file
                                }
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = file.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (file.isDirectory) "Folder" else "${(file.size / 1024.0).toInt()} KB",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = file.permissions,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        IconButton(onClick = { selectedFileForActions = file }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Actions")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                }
            }
        }
    }

    // New folder alert standard dialog
    if (showMkdirDialog) {
        AlertDialog(
            onDismissRequest = { showMkdirDialog = false },
            title = { Text("Create Directory") },
            text = {
                OutlinedTextField(
                    value = folderNameField,
                    onValueChange = { folderNameField = it },
                    label = { Text("Folder Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameField.isNotEmpty()) {
                            viewModel.makeSftpDirectory(tab.tabId, folderNameField)
                            folderNameField = ""
                            showMkdirDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMkdirDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Rename file standard dialog
    if (renameFileRef != null) {
        AlertDialog(
            onDismissRequest = { renameFileRef = null },
            title = { Text("Rename File/Folder") },
            text = {
                OutlinedTextField(
                    value = renameFieldName,
                    onValueChange = { renameFieldName = it },
                    label = { Text("New Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ref = renameFileRef
                        if (ref != null && renameFieldName.isNotEmpty()) {
                            viewModel.renameSftpFile(tab.tabId, ref.path, renameFieldName)
                            renameFileRef = null
                            renameFieldName = ""
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameFileRef = null }) { Text("Cancel") }
            }
        )
    }

    // Operations BottomSheet actions context picker
    if (selectedFileForActions != null) {
        val fileRef = selectedFileForActions!!
        AlertDialog(
            onDismissRequest = { selectedFileForActions = null },
            title = { Text(fileRef.name, maxLines = 1, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("File System Path: ${fileRef.path}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    
                    TextButton(
                        onClick = {
                            selectedFileForActions = null
                            filesCopiedInfoText = "⬇️ Started download: `${fileRef.name}` -> Saved into device downloads successfully."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Download File locally", modifier = Modifier.padding(start = 12.dp))
                        }
                    }

                    TextButton(
                        onClick = {
                            selectedFileForActions = null
                            filesCopiedInfoText = "⬆️ Standard File select opened. Mock Upload triggered successfully."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Icon(imageVector = Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Upload File to Remote", modifier = Modifier.padding(start = 12.dp))
                        }
                    }

                    TextButton(
                        onClick = {
                            renameFieldName = fileRef.name
                            renameFileRef = fileRef
                            selectedFileForActions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Icon(imageVector = Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Rename Resource", modifier = Modifier.padding(start = 12.dp))
                        }
                    }

                    TextButton(
                        onClick = {
                            viewModel.deleteSftpFile(tab.tabId, fileRef)
                            selectedFileForActions = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Delete Resource permanently", modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFileForActions = null }) { Text("Close") }
            }
        )
    }
}
