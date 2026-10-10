package com.abletonremote.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abletonremote.data.db.ServerDao
import com.abletonremote.data.db.ServerEntity
import com.abletonremote.data.model.ConnectionState
import com.abletonremote.data.model.ServerConfig
import com.abletonremote.osc.DiscoveredServer
import com.abletonremote.osc.NetworkScanner
import com.abletonremote.osc.OscConnectionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ConnectionViewModel(
    private val serverDao: ServerDao,
    private val connectionManager: OscConnectionManager
) : ViewModel() {

    val servers: StateFlow<List<ServerEntity>> = serverDao.getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeConnectionId = connectionManager.activeConnectionId

    val debugLog: StateFlow<List<String>> = connectionManager.debugLog

    // Re-poll connection states every 500ms so the dots update promptly
    val connectionStates: StateFlow<Map<String, ConnectionState>> =
        connectionManager.connections.flatMapLatest { conns ->
            if (conns.isEmpty()) flowOf(emptyMap())
            else {
                val stateFlows = conns.map { (id, conn) ->
                    conn.connectionState.map { state -> id to state }
                }
                combine(stateFlows) { pairs -> pairs.toMap() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _editingServer = MutableStateFlow<ServerEntity?>(null)
    val editingServer: StateFlow<ServerEntity?> = _editingServer.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers.asStateFlow()

    fun showAdd() { _showAddDialog.value = true }
    fun hideDialog() {
        _showAddDialog.value = false
        _editingServer.value = null
    }
    fun editServer(server: ServerEntity) { _editingServer.value = server }

    fun saveServer(name: String, host: String, sendPort: Int, receivePort: Int) {
        viewModelScope.launch {
            val entity = _editingServer.value?.copy(
                name = name, host = host, sendPort = sendPort, receivePort = receivePort
            ) ?: ServerEntity(
                name = name, host = host, sendPort = sendPort, receivePort = receivePort
            )
            serverDao.upsert(entity)
            hideDialog()
        }
    }

    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch {
            connectionManager.disconnect(server.id.toString())
            serverDao.delete(server)
        }
    }

    fun setDefault(server: ServerEntity) {
        viewModelScope.launch {
            serverDao.clearDefaults()
            serverDao.setDefault(server.id)
        }
    }

    fun connectTo(server: ServerEntity) {
        viewModelScope.launch {
            val config = ServerConfig(
                id = server.id, name = server.name,
                host = server.host, sendPort = server.sendPort,
                receivePort = server.receivePort
            )
            connectionManager.connect(config)
            serverDao.updateLastConnected(server.id, System.currentTimeMillis())
        }
    }

    fun disconnectFrom(server: ServerEntity) {
        connectionManager.disconnect(server.id.toString())
    }

    fun scanNetwork() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            _discoveredServers.value = emptyList()
            NetworkScanner.scanForAbleton { found ->
                _discoveredServers.value = _discoveredServers.value + found
            }
            _isScanning.value = false
        }
    }

    fun addDiscoveredServer(server: DiscoveredServer) {
        viewModelScope.launch {
            serverDao.upsert(
                ServerEntity(name = server.name, host = server.host)
            )
            _discoveredServers.value = _discoveredServers.value - server
        }
    }

    fun clearDiscovered() {
        _discoveredServers.value = emptyList()
    }
}
