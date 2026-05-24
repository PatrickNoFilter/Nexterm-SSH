package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SshViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Initialize premium SSH Client controller
                val viewModel: SshViewModel = viewModel()
                
                val hosts by viewModel.allHosts.collectAsState()
                val keys by viewModel.allKeys.collectAsState()
                val snippets by viewModel.allSnippets.collectAsState()
                val tabs by viewModel.sessions.collectAsState()
                val activeTabId by viewModel.activeTabId.collectAsState()

                var showTerminalScreen by remember { mutableStateOf(false) }

                // Intercept hardware Android back button to navigate from Terminal directly to Dashboard
                if (showTerminalScreen) {
                    BackHandler {
                        showTerminalScreen = false
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = showTerminalScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                        },
                        label = "screen_navigation_animated"
                    ) { terminalActive ->
                        if (terminalActive) {
                            TerminalScreen(
                                viewModel = viewModel,
                                tabs = tabs,
                                activeTabId = activeTabId,
                                allSnippets = snippets,
                                onBackToDashboard = { showTerminalScreen = false },
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            DashboardScreen(
                                viewModel = viewModel,
                                hosts = hosts,
                                keys = keys,
                                snippets = snippets,
                                onOpenTerminal = { showTerminalScreen = true },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
