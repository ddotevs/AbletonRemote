package com.abletonremote.osc

import com.abletonremote.data.model.ConnectionState
import com.illposed.osc.MessageSelector
import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.transport.OSCPortIn
import com.illposed.osc.transport.OSCPortOut
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.InetAddress
import java.net.InetSocketAddress

class OscConnection(
    val id: String,
    val host: String,
    val sendPort: Int = 11000,
    val receivePort: Int = 11001
) {
    private var portOut: OSCPortOut? = null
    private var portIn: OSCPortIn? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Pair<String, List<Any>>>(
        extraBufferCapacity = 256
    )
    val incomingMessages: SharedFlow<Pair<String, List<Any>>> = _incomingMessages.asSharedFlow()

    private val listeners = mutableSetOf<String>()
    private var heartbeatJob: Job? = null

    private val allMessageSelector = object : MessageSelector {
        override fun isInfoRequired(): Boolean = false
        override fun matches(messageEvent: OSCMessageEvent): Boolean = true
    }

    fun connect() {
        scope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING

                val remote = InetSocketAddress(InetAddress.getByName(host), sendPort)
                portOut = OSCPortOut(remote)

                val local = InetSocketAddress(InetAddress.getByName("0.0.0.0"), receivePort)
                portIn = OSCPortIn(local)
                portIn!!.dispatcher.addListener(allMessageSelector,
                    OSCMessageListener { event ->
                        val msg = event.message
                        scope.launch {
                            _incomingMessages.emit(msg.address to msg.arguments)
                        }
                    }
                )
                portIn!!.startListening()

                // Send test and wait briefly for a reply
                val testMsg = OSCMessage("/live/test")
                portOut!!.send(testMsg)

                _connectionState.value = ConnectionState.CONNECTED
                startHeartbeat()
            } catch (e: Exception) {
                e.printStackTrace()
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        listeners.clear()
        try {
            portIn?.stopListening()
            portIn?.close()
        } catch (_: Exception) {}
        try {
            portOut?.close()
        } catch (_: Exception) {}
        portIn = null
        portOut = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun send(address: String, vararg args: Any) {
        scope.launch {
            try {
                val msg = OSCMessage(address, args.toList())
                portOut?.send(msg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun messagesAt(address: String): Flow<List<Any>> {
        return incomingMessages
            .filter { it.first == address }
            .map { it.second }
    }

    fun startListening(property: String, vararg args: Any) {
        val listenAddress = property.replace("/get/", "/start_listen/")
        listeners.add(listenAddress)
        send(listenAddress, *args)
    }

    fun stopListening(property: String, vararg args: Any) {
        val listenAddress = property.replace("/get/", "/stop_listen/")
        listeners.remove(listenAddress.replace("/stop_listen/", "/start_listen/"))
        send(listenAddress, *args)
    }

    fun stopAllListeners() {
        listeners.toList().forEach { addr ->
            val stopAddr = addr.replace("/start_listen/", "/stop_listen/")
            send(stopAddr)
        }
        listeners.clear()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(5000)
                try {
                    val msg = OSCMessage("/live/test")
                    portOut?.send(msg)
                } catch (_: Exception) {
                    _connectionState.value = ConnectionState.ERROR
                }
            }
        }
    }
}
