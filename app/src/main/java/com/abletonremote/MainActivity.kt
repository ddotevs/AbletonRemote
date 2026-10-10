package com.abletonremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.abletonremote.data.model.ConnectionState
import com.abletonremote.ui.connection.ConnectionScreen
import com.abletonremote.ui.connection.ConnectionViewModel
import com.abletonremote.ui.mixer.MixerScreen
import com.abletonremote.ui.mixer.MixerViewModel
import com.abletonremote.ui.navigation.Screen
import com.abletonremote.ui.theme.*
import com.abletonremote.ui.transport.TransportScreen
import com.abletonremote.ui.transport.TransportViewModel

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AbletonRemoteApp
        val connectionViewModel = ConnectionViewModel(
            app.database.serverDao(), app.connectionManager
        )
        val transportViewModel = TransportViewModel(app.repository)
        val mixerViewModel = MixerViewModel(app.repository)

        setContent {
            AbletonRemoteTheme {
                val navController = rememberNavController()
                val currentEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentEntry?.destination?.route

                val activeConnState by app.connectionManager.activeConnectionState
                    .collectAsState(initial = ConnectionState.DISCONNECTED)

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (activeConnState) {
                                                    ConnectionState.CONNECTED -> AbletonGreen
                                                    ConnectionState.CONNECTING -> AbletonYellow
                                                    ConnectionState.ERROR -> AbletonRed
                                                    ConnectionState.DISCONNECTED -> AbletonTextSecondary
                                                }
                                            )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Ableton Remote")
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = AbletonDark
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = AbletonDark) {
                            Screen.entries.forEach { screen ->
                                NavigationBarItem(
                                    selected = currentRoute == screen.route,
                                    onClick = {
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(screen.icon, screen.label) },
                                    label = { Text(screen.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AbletonAccent,
                                        selectedTextColor = AbletonAccent,
                                        unselectedIconColor = AbletonTextSecondary,
                                        unselectedTextColor = AbletonTextSecondary,
                                        indicatorColor = AbletonAccent.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.SERVERS.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(AbletonDark)
                    ) {
                        composable(Screen.SERVERS.route) {
                            ConnectionScreen(connectionViewModel)
                        }
                        composable(Screen.TRANSPORT.route) {
                            TransportScreen(transportViewModel)
                        }
                        composable(Screen.MIXER.route) {
                            MixerScreen(mixerViewModel)
                        }
                    }
                }
            }
        }
    }
}
