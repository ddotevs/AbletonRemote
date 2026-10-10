package com.abletonremote.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.abletonremote.data.db.ServerEntity
import com.abletonremote.data.model.ConnectionState
import com.abletonremote.osc.DiscoveredServer
import com.abletonremote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel) {
    val servers by viewModel.servers.collectAsState()
    val activeId by viewModel.activeConnectionId.collectAsState()
    val connectionStates by viewModel.connectionStates.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val editingServer by viewModel.editingServer.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredServers by viewModel.discoveredServers.collectAsState()

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { viewModel.scanNetwork() },
                    containerColor = AbletonBlue
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AbletonDark
                        )
                    } else {
                        Icon(Icons.Default.Wifi, contentDescription = "Scan Network")
                    }
                }
                FloatingActionButton(
                    onClick = { viewModel.showAdd() },
                    containerColor = AbletonAccent
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Server")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Emulator hint
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AbletonCard)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info, null,
                            modifier = Modifier.size(16.dp),
                            tint = AbletonBlue
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Emulator? Use 10.0.2.2 to reach this Mac. " +
                            "On a real device, use your LAN IP.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AbletonTextSecondary
                        )
                    }
                }
            }

            // Discovered servers section
            if (discoveredServers.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Discovered on Network",
                            style = MaterialTheme.typography.titleSmall,
                            color = AbletonBlue
                        )
                        TextButton(onClick = { viewModel.clearDiscovered() }) {
                            Text("Clear", color = AbletonTextSecondary)
                        }
                    }
                }
                items(discoveredServers) { discovered ->
                    DiscoveredServerCard(
                        server = discovered,
                        onAdd = { viewModel.addDiscoveredServer(discovered) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (isScanning && discoveredServers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AbletonSurface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AbletonBlue
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Scanning local network for AbletonOSC...",
                                color = AbletonTextSecondary,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Saved servers section
            if (servers.isNotEmpty()) {
                item {
                    Text(
                        "Saved Servers",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(servers, key = { it.id }) { server ->
                    ServerCard(
                        server = server,
                        isActive = activeId == server.id.toString(),
                        state = connectionStates[server.id.toString()] ?: ConnectionState.DISCONNECTED,
                        onConnect = { viewModel.connectTo(server) },
                        onDisconnect = { viewModel.disconnectFrom(server) },
                        onEdit = { viewModel.editServer(server) },
                        onDelete = { viewModel.deleteServer(server) },
                        onSetDefault = { viewModel.setDefault(server) }
                    )
                }
            }

            if (servers.isEmpty() && !isScanning && discoveredServers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Dns, contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = AbletonTextSecondary
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("No servers configured", color = AbletonTextSecondary)
                            Spacer(Modifier.height(8.dp))
                            Text("Tap + to add manually, or scan your network",
                                color = AbletonTextSecondary,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingServer != null) {
        ServerDialog(
            existing = editingServer,
            onDismiss = { viewModel.hideDialog() },
            onSave = { name, host, send, recv -> viewModel.saveServer(name, host, send, recv) }
        )
    }
}

@Composable
fun DiscoveredServerCard(
    server: DiscoveredServer,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AbletonBlue.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Wifi, null, tint = AbletonBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(server.host, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "AbletonOSC detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = AbletonBlue,
                    fontStyle = FontStyle.Italic
                )
            }
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = AbletonBlue),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("Add")
            }
        }
    }
}

@Composable
fun ServerCard(
    server: ServerEntity,
    isActive: Boolean,
    state: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    val stateColor = when (state) {
        ConnectionState.CONNECTED -> AbletonGreen
        ConnectionState.CONNECTING -> AbletonYellow
        ConnectionState.ERROR -> AbletonRed
        ConnectionState.DISCONNECTED -> AbletonTextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) AbletonCard else AbletonSurface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(stateColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    if (server.isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "DEFAULT",
                            style = MaterialTheme.typography.labelSmall,
                            color = AbletonAccent
                        )
                    }
                }
                Text(
                    "${server.host}:${server.sendPort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AbletonTextSecondary
                )
            }

            if (state == ConnectionState.CONNECTED) {
                IconButton(onClick = onDisconnect) {
                    Icon(Icons.Default.LinkOff, "Disconnect", tint = AbletonRed)
                }
            } else {
                IconButton(onClick = onConnect) {
                    Icon(Icons.Default.Link, "Connect", tint = AbletonGreen)
                }
            }

            var showMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, "Options")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() })
                DropdownMenuItem(text = { Text("Set as Default") }, onClick = { showMenu = false; onSetDefault() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

@Composable
fun ServerDialog(
    existing: ServerEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var sendPort by remember { mutableStateOf((existing?.sendPort ?: 11000).toString()) }
    var receivePort by remember { mutableStateOf((existing?.receivePort ?: 11001).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Server" else "Add Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Studio A") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text("IP Address") },
                    placeholder = { Text("192.168.1.100 or 10.0.2.2") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sendPort, onValueChange = { sendPort = it },
                        label = { Text("Send Port") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = receivePort, onValueChange = { receivePort = it },
                        label = { Text("Receive Port") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.ifBlank { host },
                        host,
                        sendPort.toIntOrNull() ?: 11000,
                        receivePort.toIntOrNull() ?: 11001
                    )
                },
                enabled = host.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
