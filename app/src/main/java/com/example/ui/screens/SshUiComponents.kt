package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SshHost
import com.example.data.model.SshKey
import com.example.data.model.SshSnippet
import com.example.ssh.SftpFile
import com.example.ssh.SshSessionTab
import com.example.ui.theme.TerminalTheme
import com.example.ui.viewmodel.SshViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: SshViewModel,
    hosts: List<SshHost>,
    keys: List<SshKey>,
    snippets: List<SshSnippet>,
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Hosts, 1: Keys, 2: Snippets, 3: Settings
    var showAddHostDialog by remember { mutableStateOf(false) }
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var showAddSnippetDialog by remember { mutableStateOf(false) }
    var hostToEdit by remember { mutableStateOf<SshHost?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val googleEmail by viewModel.googleEmail.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    val tabs = listOf("Hosts", "SSH Keys", "Snippets", "Preferences")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Nexterm SSH",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "Nexterm SSH",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    val statusText = when {
                        googleEmail == null -> "OFFLINE"
                        syncStatus == "syncing" -> "SYNCING"
                        else -> "SYNCED"
                    }
                    val statusIcon = when {
                        googleEmail == null -> Icons.Default.CloudOff
                        syncStatus == "syncing" -> Icons.Default.Sync
                        else -> Icons.Default.CloudDone
                    }
                    val containerColor = when {
                        googleEmail == null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        syncStatus == "syncing" -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                    val contentColor = when {
                        googleEmail == null -> MaterialTheme.colorScheme.onErrorContainer
                        syncStatus == "syncing" -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }

                    Row(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(containerColor)
                            .clickable { selectedTab = 3 }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (syncStatus == "syncing") {
                            CircularProgressIndicator(
                                color = contentColor,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = statusText,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (selectedTab < 3) {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> {
                                hostToEdit = null
                                showAddHostDialog = true
                            }
                            1 -> showAddKeyDialog = true
                            2 -> showAddSnippetDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
                modifier = Modifier.border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            ) {
                listOf(
                    Triple("Hosts", Icons.Default.Storage, 0),
                    Triple("Keys", Icons.Default.VpnKey, 1),
                    Triple("Snippets", Icons.Default.Code, 2),
                    Triple("Config", Icons.Default.Settings, 3)
                ).forEach { (label, icon, index) ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> HostsTab(
                        viewModel = viewModel,
                        hosts = hosts,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onEditHost = {
                            hostToEdit = it
                            showAddHostDialog = true
                        },
                        onOpenTerminal = onOpenTerminal
                    )
                    1 -> KeysTab(
                        viewModel = viewModel,
                        keys = keys
                    )
                    2 -> SnippetsTab(
                        viewModel = viewModel,
                        snippets = snippets
                    )
                    3 -> PreferencesTab(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Dialog components
    if (showAddHostDialog) {
        HostDialog(
            host = hostToEdit,
            keys = keys,
            onDismiss = { showAddHostDialog = false },
            onSave = { label, host, port, username, authType, secret, keyId, groupName ->
                viewModel.saveHost(
                    label = label,
                    host = host,
                    port = port,
                    username = username,
                    authType = authType,
                    secret = secret,
                    keyId = keyId,
                    groupName = groupName,
                    existingHostId = hostToEdit?.id ?: 0
                )
                showAddHostDialog = false
            }
        )
    }

    if (showAddKeyDialog) {
        KeyDialog(
            onDismiss = { showAddKeyDialog = false },
            onSaveFile = { label, keyText, passPhrase ->
                viewModel.importKey(label, keyText, passPhrase)
                showAddKeyDialog = false
            },
            onGenerate = { label ->
                viewModel.generateNewKeypair(label)
                showAddKeyDialog = false
            }
        )
    }

    if (showAddSnippetDialog) {
        SnippetDialog(
            onDismiss = { showAddSnippetDialog = false },
            onSave = { label, cmd ->
                viewModel.saveSnippet(label, cmd)
                showAddSnippetDialog = false
            }
        )
    }
}

@Composable
fun HostsTab(
    viewModel: SshViewModel,
    hosts: List<SshHost>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onEditHost: (SshHost) -> Unit,
    onOpenTerminal: () -> Unit
) {
    val keys by viewModel.allKeys.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val activeCount = sessions.count { it.isConnected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Quick Connect Form & Search Header
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search Hosts/IP...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // Quick Stats row directly matching the professional design HTML
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // SSH Keys Stat
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${keys.size}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SSH KEYS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.70f),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Active SFTP / Connections Stat
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$activeCount",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ACTIVE SESSIONS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.70f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Section Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT HOSTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                letterSpacing = 1.sp
            )
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filter List",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        val filteredHosts = hosts.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                    it.host.contains(searchQuery, ignoreCase = true) ||
                    it.username.contains(searchQuery, ignoreCase = true)
        }

        if (filteredHosts.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Dns,
                title = "No Connections Found",
                description = "Tap the '+' button below to configure your first persistent SSH Server connection profile."
            )
        } else {
            // Group lists of configurations in Folders (Termius style!)
            val groups = filteredHosts.groupBy { it.groupName }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEach { (groupLabel, hostList) ->
                    item {
                        Text(
                            text = groupLabel.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                    }

                    items(hostList, key = { it.id }) { host ->
                        val isFirstItem = hostList.indexOf(host) == 0
                        // Highly polished card design with border indicators
                        OutlinedCard(
                            onClick = {
                                viewModel.connectToHost(host)
                                onOpenTerminal()
                            },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isFirstItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isFirstItem) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rounded icon matching the HTML template perfectly (e.g. dns vs computer)
                                val iconToUse = if (host.label.contains("AWS", ignoreCase = true) || host.label.contains("Prod", ignoreCase = true)) {
                                    Icons.Default.Dns
                                } else {
                                    Icons.Default.Computer
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isFirstItem) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconToUse,
                                        contentDescription = "Host type",
                                        tint = if (isFirstItem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = host.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isFirstItem) {
                                            // Tiny glowing status light indicator
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF22C55E))
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${host.username}@${host.host}:${host.port}",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Security auth indicator tags
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (host.authType == "KEY") "ECDSA-256" else "PASSWORD",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "PORT ${host.port}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Row {
                                    IconButton(
                                        onClick = { onEditHost(host) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Profile",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteHost(host) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove Profile",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeysTab(viewModel: SshViewModel, keys: List<SshKey>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MANAGED SSH KEYRINGS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                letterSpacing = 1.sp
            )
        }

        if (keys.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.VpnKey,
                title = "No Keyrings Created",
                description = "Generate a secure 2048-bit RSA key Pair client-side, or import your existing server private key."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(keys, key = { it.id }) { key ->
                    OutlinedCard(
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = "Ssh Key",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 14.dp)
                                ) {
                                Text(key.label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    text = "RSA Key File (Encrypted on Disk)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!key.publicKey.isNullOrEmpty()) {
                                    Text(
                                        text = "Public PEM Key Available",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.deleteKey(key) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete key",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SnippetsTab(viewModel: SshViewModel, snippets: List<SshSnippet>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "COMMAND SHORTCUT SNIPPETS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                letterSpacing = 1.sp
            )
        }

        if (snippets.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Code,
                title = "No Snippets Active",
                description = "Define popular complex scripts (e.g. system status, logs) so you can run them with a single tap inside active SSH terminals."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(snippets, key = { it.id }) { snippet ->
                    OutlinedCard(
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Code Snippet",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 14.dp)
                            ) {
                                Text(snippet.label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 6.dp)
                                ) {
                                    Text(
                                        text = snippet.commandText,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.deleteSnippet(snippet) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete snippet",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesTab(viewModel: SshViewModel) {
    val currentTheme by viewModel.terminalTheme.collectAsState()
    val isCustomMasterSet by viewModel.isMasterKeySetCustom.collectAsState()
    val masterKeyPhrase by viewModel.masterKeyPhrase.collectAsState()

    val googleEmail by viewModel.googleEmail.collectAsState()
    val googleName by viewModel.googleName.collectAsState()
    val isAutoSyncEnabled by viewModel.isAutoSyncEnabled.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val lastSyncedTime by viewModel.lastSyncedTime.collectAsState()
    val syncDevices by viewModel.syncDevices.collectAsState()

    var masterPhraseField by remember { mutableStateOf("") }
    var showGoogleSignInDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Theme selection card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Terminal Customizer Theme",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Personalize colors on full active SSH session terminal panels instantly.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Grid list of themes
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TerminalTheme.entries.forEach { theme ->
                        val isSelected = currentTheme == theme
                        Button(
                            onClick = { viewModel.selectTerminalTheme(theme) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(theme.title, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Encrypted credentials encryption & local sync option
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCustomMasterSet) Icons.Default.Shield else Icons.Default.VpnLock,
                        contentDescription = "Security Status",
                        tint = if (isCustomMasterSet) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Local Sync Encryption Locks",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isCustomMasterSet) {
                        "🔒 SQLite columns are strongly synchronized and encrypted using your personal master passphrase. Credentials cannot be read by inspect tools."
                    } else {
                        "🔓 Default encryption key is active. For ultimate defense security, set a custom Master sync passphrase to isolate credentials locally."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (!isCustomMasterSet) {
                    OutlinedTextField(
                        value = masterPhraseField,
                        onValueChange = { masterPhraseField = it },
                        label = { Text("Set Custom Master Keyphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (masterPhraseField.length >= 4) {
                                viewModel.updateMasterKeyphrase(masterPhraseField)
                                masterPhraseField = ""
                            }
                        },
                        enabled = masterPhraseField.length >= 4,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apply & Cryptograph Lock Profiles")
                    }
                } else {
                    OutlinedTextField(
                        value = "••••••••••••••••",
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Cryptographic Lock: Bound & Secure") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.resetMasterKeyphrase() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Master Key & Purge Personal Lock")
                    }
                }
            }
        }

        // Nexterm Cloud Sync Dashboard Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Cloud Synchronizer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Nexterm Sync & Connected Devices",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Seamlessly keep your private hosts, snippets, and connection identities in lock-step across your tablets, phones, and desktop devices safely.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (googleEmail == null) {
                    // Sign-in promo screen with Google-styled button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Link Your Google Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Uses 100% free private app sandboxing on Google Drive. No external database subscription required.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showGoogleSignInDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF1F1F1F)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF747775)),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    // Custom visual Google "G" representation
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(Color(0xFFEA4335)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "G",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Sign in with Google",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        letterSpacing = 0.1.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Signed In Status & Management Panel
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (googleName ?: "G").take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = googleName ?: "Google User",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = googleEmail ?: "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.signOutGoogle() }) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "Sign Out",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Auto sync switcher & manual sync button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Sync Databases",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Silently pushes incremental modifications in background",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAutoSyncEnabled,
                                onCheckedChange = { viewModel.toggleAutoSync(it) }
                            )
                        }

                        // Sync button card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Sync Status",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = lastSyncedTime ?: "Never synchronized yet",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { viewModel.triggerSync() },
                                enabled = syncStatus != "syncing",
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (syncStatus == "synced") Color(0xFF22C55E) else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (syncStatus == "syncing") {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("Syncing...", fontSize = 12.sp)
                                    } else if (syncStatus == "synced") {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text("Success", fontSize = 12.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text("Sync Now", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Registered Sync Devices Section
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.40f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "MY REGISTERED DEVICES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                syncDevices.forEach { device ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (device.isCurrent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                                else Color.Transparent
                                            )
                                            .padding(vertical = 8.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when (device.type) {
                                                "DESKTOP" -> Icons.Default.Computer
                                                "TABLET" -> Icons.Default.TabletAndroid
                                                else -> Icons.Default.PhoneAndroid
                                            },
                                            contentDescription = null,
                                            tint = if (device.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = device.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (device.isCurrent) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "THIS DEVICE",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = device.lastSynced,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Glow light online dot
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (device.isOnline) Color(0xFF22C55E) else Color(0xFF94A3B8))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGoogleSignInDialog) {
        var prefilledEmail by remember { mutableStateOf("patrisius.wr@gmail.com") }
        var inputName by remember { mutableStateOf("Patrisius") }

        AlertDialog(
            onDismissRequest = { showGoogleSignInDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEA4335)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Select Google Account", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Sign in to Nexterm SSH Cloud sync ring with your personal configuration backup account.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = prefilledEmail,
                        onValueChange = { prefilledEmail = it },
                        label = { Text("Google Account Email") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (prefilledEmail.isNotEmpty()) {
                            viewModel.signInGoogle(prefilledEmail, inputName)
                            showGoogleSignInDialog = false
                        }
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleSignInDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmptyStateView(icon: ImageVector, title: String, description: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

// --- CONFIG DIALOG IMPLEMENTATIONS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostDialog(
    host: SshHost?,
    keys: List<SshKey>,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, String, String, Int?, String) -> Unit
) {
    var label by remember { mutableStateOf(host?.label ?: "") }
    var hostName by remember { mutableStateOf(host?.host ?: "") }
    var port by remember { mutableStateOf(host?.port?.toString() ?: "22") }
    var username by remember { mutableStateOf(host?.username ?: "root") }
    var authType by remember { mutableStateOf(host?.authType ?: "PASSWORD") } // or "KEY"
    var secretPassword by remember { mutableStateOf("") }
    var selectedKeyId by remember { mutableStateOf(host?.keyId) }
    var groupName by remember { mutableStateOf(host?.groupName ?: "Servers") }

    var expandedKeys by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (host == null) "Configure SSH Profile" else "Edit SSH Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Profile Name (e.g. Prod AWS)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hostName,
                        onValueChange = { hostName = it },
                        label = { Text("Hostname / IP") },
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Folder Group (e.g. AWS, Servers)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Select Auth type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auth Type:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = authType == "PASSWORD", onClick = { authType = "PASSWORD" })
                        Text("Password", fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = authType == "KEY", onClick = { authType = "KEY" })
                        Text("SSH Key", fontSize = 14.sp)
                    }
                }

                if (authType == "PASSWORD") {
                    OutlinedTextField(
                        value = secretPassword,
                        onValueChange = { secretPassword = it },
                        label = { Text(if (host != null) "Password (Leave blank to keep current)" else "Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Custom keydropdown select
                    ExposedDropdownMenuBox(
                        expanded = expandedKeys,
                        onExpandedChange = { expandedKeys = !expandedKeys }
                    ) {
                        val currentSelectionText = keys.find { it.id == selectedKeyId }?.label ?: "Select Key Identity..."
                        OutlinedTextField(
                            value = currentSelectionText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Identity Key Pair") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedKeys) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedKeys,
                            onDismissRequest = { expandedKeys = false }
                        ) {
                            if (keys.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No custom keys setup. Add key first.") },
                                    onClick = { expandedKeys = false }
                                )
                            } else {
                                keys.forEach { key ->
                                    DropdownMenuItem(
                                        text = { Text(key.label) },
                                        onClick = {
                                            selectedKeyId = key.id
                                            expandedKeys = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        label,
                        hostName,
                        port.toIntOrNull() ?: 22,
                        username,
                        authType,
                        secretPassword,
                        selectedKeyId,
                        groupName
                    )
                },
                enabled = hostName.isNotEmpty() && username.isNotEmpty()
            ) {
                Text("Save Server Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun KeyDialog(
    onDismiss: () -> Unit,
    onSaveFile: (String, String, String) -> Unit,
    onGenerate: (String) -> Unit
) {
    var keyMode by remember { mutableStateOf(0) } // 0: Import, 1: Generate Client-side
    var label by remember { mutableStateOf("") }
    var privateKeyPem by remember { mutableStateOf("") }
    var passphraseText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Key Identity", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabRow(selectedTabIndex = keyMode) {
                    Tab(selected = keyMode == 0, onClick = { keyMode = 0 }, text = { Text("Import") })
                    Tab(selected = keyMode == 1, onClick = { keyMode = 1 }, text = { Text("Generate RSA") })
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Key Profile Name (e.g. AWS Core)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (keyMode == 0) {
                    OutlinedTextField(
                        value = privateKeyPem,
                        onValueChange = { privateKeyPem = it },
                        label = { Text("Paste RSA/ED25519 Private Key PEM Data") },
                        placeholder = { Text("-----BEGIN RSA PRIVATE KEY-----\n...") },
                        maxLines = 8,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    OutlinedTextField(
                        value = passphraseText,
                        onValueChange = { passphraseText = it },
                        label = { Text("Private Key Passphrase (Optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Generates a fully secure standard 2048-bit RSA key Pair directly inside the device. The secure private parameters will be encrypted and saved locally in Room.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (keyMode == 0) {
                        onSaveFile(label, privateKeyPem, passphraseText)
                    } else {
                        onGenerate(label)
                    }
                },
                enabled = label.isNotEmpty() && (keyMode == 1 || privateKeyPem.isNotEmpty())
            ) {
                Text(if (keyMode == 0) "Import Identity" else "Generate Identity Key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SnippetDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Command Shortcut", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Shortcut Name (e.g. System Stats)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Bash Command Text") },
                    placeholder = { Text("uname -a && lscpu && df -h") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label, command) },
                enabled = label.isNotEmpty() && command.isNotEmpty()
            ) {
                Text("Add Shortcut")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
