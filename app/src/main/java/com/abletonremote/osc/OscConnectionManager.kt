package com.abletonremote.osc

import com.abletonremote.data.model.ConnectionState
import com.abletonremote.data.model.ServerConfig
import kotlinx.coroutines.flow.*

class OscConnectionManager {

    private val _connections = MutableStateFlow<Map<String, OscConnection>>(emptyMap())
    val connections: StateFlow<Map<String, OscConnection>> = _connections.asStateFlow()

    private val _activeConnectionId = MutableStateFlow<String?>(null)
    val activeConnectionId: StateFlow<String?> = _activeConnectionId.asStateFlow()

    private val _debugLog = MutableStateFlow<List<String>>(emptyList())
    val debugLog: StateFlow<List<String>> = _debugLog.asStateFlow()

    fun log(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date())
        _debugLog.value = (_debugLog.value + "[$timestamp] $msg").takeLast(50)
    }

    val activeConnection: Flow<OscConnection?> = combine(
        _connections, _activeConnectionId
    ) { conns, activeId ->
        activeId?.let { conns[it] }
    }

    val activeConnectionState: Flow<ConnectionState> = activeConnection.flatMapLatest { conn ->
        conn?.connectionState ?: flowOf(ConnectionState.DISCONNECTED)
    }

    fun connect(config: ServerConfig): OscConnection {
        val id = config.id.toString()
        val existing = _connections.value[id]
        if (existing != null && existing.connectionState.value == ConnectionState.CONNECTED) {
            _activeConnectionId.value = id
            return existing
        }

        existing?.disconnect()

        val connection = OscConnection(
            id = id,
            host = config.host,
            sendPort = config.sendPort,
            receivePort = config.receivePort,
            log = { msg -> log(msg) }
        )
        _connections.value = _connections.value + (id to connection)
        _activeConnectionId.value = id
        connection.connect()
        return connection
    }

    fun switchTo(serverId: String) {
        if (_connections.value.containsKey(serverId)) {
            _activeConnectionId.value = serverId
        }
    }

    fun disconnect(serverId: String) {
        _connections.value[serverId]?.let { conn ->
            conn.stopAllListeners()
            conn.disconnect()
            _connections.value = _connections.value - serverId
            if (_activeConnectionId.value == serverId) {
                _activeConnectionId.value = _connections.value.keys.firstOrNull()
            }
        }
    }

    fun disconnectAll() {
        _connections.value.values.forEach {
            it.stopAllListeners()
            it.disconnect()
        }
        _connections.value = emptyMap()
        _activeConnectionId.value = null
    }
}
