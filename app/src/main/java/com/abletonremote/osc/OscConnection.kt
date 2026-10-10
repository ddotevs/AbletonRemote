package com.abletonremote.osc

import com.abletonremote.data.model.ConnectionState
import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCParser
import com.illposed.osc.OSCSerializerAndParserBuilder
import com.illposed.osc.argument.handler.*
import com.illposed.osc.transport.OSCPortOut
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class OscConnection(
    val id: String,
    val host: String,
    val sendPort: Int = 11000,
    val receivePort: Int = 11001,
    private val log: (String) -> Unit = {}
) {
    private var portOut: OSCPortOut? = null
    private var receiveSocket: DatagramSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var receiveJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Pair<String, List<Any>>>(
        extraBufferCapacity = 256
    )
    val incomingMessages: SharedFlow<Pair<String, List<Any>>> = _incomingMessages.asSharedFlow()

    private val listeners = mutableSetOf<String>()
    private var heartbeatJob: Job? = null

    companion object {
        fun createAndroidSafeBuilder(): OSCSerializerAndParserBuilder {
            val builder = OSCSerializerAndParserBuilder()
            builder.setUsingDefaultHandlers(false)

            val safeHandlers = listOf(
                BlobArgumentHandler.INSTANCE,
                BooleanFalseArgumentHandler.INSTANCE,
                BooleanTrueArgumentHandler.INSTANCE,
                CharArgumentHandler.INSTANCE,
                ColorArgumentHandler.INSTANCE,
                DoubleArgumentHandler.INSTANCE,
                FloatArgumentHandler.INSTANCE,
                ImpulseArgumentHandler.INSTANCE,
                IntegerArgumentHandler.INSTANCE,
                LongArgumentHandler.INSTANCE,
                MidiMessageArgumentHandler.INSTANCE,
                NullArgumentHandler.INSTANCE,
                TimeTag64ArgumentHandler.INSTANCE,
                UnsignedIntegerArgumentHandler.INSTANCE,
                StringArgumentHandler(),
                SymbolArgumentHandler()
            )
            for (handler in safeHandlers) {
                builder.registerArgumentHandler(handler)
            }
            return builder
        }
    }

    fun connect() {
        scope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                log("Connecting to $host:$sendPort ...")

                val builder = createAndroidSafeBuilder()
                log("Builder OK")

                val remote = InetSocketAddress(InetAddress.getByName(host), sendPort)
                log("Resolved: ${remote.address.hostAddress}:$sendPort")

                val local = InetSocketAddress(InetAddress.getByName("0.0.0.0"), receivePort)
                portOut = OSCPortOut(builder, remote, local)
                log("OSCPortOut bound to local:$receivePort -> remote:$sendPort")

                receiveSocket = DatagramSocket(null)
                receiveSocket!!.reuseAddress = true
                receiveSocket!!.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), receivePort))
                log("Receive socket bound to :$receivePort")

                val parser = builder.buildParser()
                startReceiveLoop(parser)
                log("Receive loop started")

                val testMsg = OSCMessage("/live/test")
                portOut!!.send(testMsg)
                log("Sent /live/test")

                _connectionState.value = ConnectionState.CONNECTED
                log("CONNECTED")
                startHeartbeat()
            } catch (e: Exception) {
                log("ERROR: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }

    private fun startReceiveLoop(parser: OSCParser) {
        receiveJob = scope.launch {
            val buf = ByteArray(4096)
            log("Listening for packets...")
            while (isActive) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    receiveSocket?.receive(pkt) ?: break
                    log("Recv ${pkt.length}B from ${pkt.address}:${pkt.port}")
                    val data = ByteBuffer.wrap(buf, 0, pkt.length)
                    val oscPacket = parser.convert(data)
                    if (oscPacket is OSCMessage) {
                        log("OSC: ${oscPacket.address} ${oscPacket.arguments}")
                        _incomingMessages.emit(oscPacket.address to oscPacket.arguments)
                    }
                } catch (e: java.net.SocketException) {
                    log("Socket closed")
                    break
                } catch (e: Exception) {
                    log("Recv err: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        receiveJob?.cancel()
        listeners.clear()
        try { receiveSocket?.close() } catch (_: Exception) {}
        try { portOut?.close() } catch (_: Exception) {}
        receiveSocket = null
        portOut = null
        _connectionState.value = ConnectionState.DISCONNECTED
        log("Disconnected")
    }

    fun send(address: String, vararg args: Any) {
        scope.launch {
            try {
                val msg = OSCMessage(address, args.toList())
                portOut?.send(msg)
            } catch (e: Exception) {
                log("Send err ($address): ${e.javaClass.simpleName}: ${e.message}")
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
