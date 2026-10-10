package com.abletonremote.osc

import com.abletonremote.data.model.ConnectionState
import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCSerializerAndParserBuilder
import com.illposed.osc.argument.handler.*
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
    private var socket: DatagramSocket? = null
    private var remoteAddress: InetAddress? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var receiveJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Pair<String, List<Any>>>(
        extraBufferCapacity = 512
    )
    val incomingMessages: SharedFlow<Pair<String, List<Any>>> = _incomingMessages.asSharedFlow()

    private val listeners = mutableSetOf<String>()
    private var heartbeatJob: Job? = null

    private val builder = createAndroidSafeBuilder()

    // Reusable send buffer — guarded by sendLock for thread safety
    private val sendLock = Any()
    private val sendBuffer = ByteBuffer.allocate(4096)

    companion object {
        fun createAndroidSafeBuilder(): OSCSerializerAndParserBuilder {
            val builder = OSCSerializerAndParserBuilder()
            builder.setUsingDefaultHandlers(false)
            listOf(
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
            ).forEach { builder.registerArgumentHandler(it) }
            return builder
        }
    }

    fun connect() {
        scope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                log("Connecting to $host:$sendPort ...")

                remoteAddress = InetAddress.getByName(host)
                log("Resolved: ${remoteAddress!!.hostAddress}")

                socket = DatagramSocket(null)
                socket!!.reuseAddress = true
                socket!!.bind(InetSocketAddress("0.0.0.0", receivePort))
                log("Socket bound to :$receivePort")

                val parser = builder.buildParser()
                startReceiveLoop(parser)
                log("Receive loop started")

                sendOsc("/live/test")
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

    private fun sendOsc(address: String, vararg args: Any) {
        synchronized(sendLock) {
            try {
                val msg = OSCMessage(address, args.toList())
                sendBuffer.clear()
                val receiver = com.illposed.osc.BufferBytesReceiver(sendBuffer)
                val serializer = builder.buildSerializer(receiver)
                serializer.write(msg)
                val bytes = receiver.toByteArray()
                val pkt = DatagramPacket(bytes, bytes.size, remoteAddress, sendPort)
                socket?.send(pkt)
            } catch (e: Exception) {
                log("Send err ($address): ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun startReceiveLoop(parser: com.illposed.osc.OSCParser) {
        receiveJob = scope.launch {
            val buf = ByteArray(4096)
            log("Listening for packets...")
            while (isActive) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    socket?.receive(pkt) ?: break
                    val data = ByteBuffer.wrap(buf, 0, pkt.length)
                    val oscPacket = parser.convert(data)
                    if (oscPacket is OSCMessage) {
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
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        remoteAddress = null
        _connectionState.value = ConnectionState.DISCONNECTED
        log("Disconnected")
    }

    fun send(address: String, vararg args: Any) {
        scope.launch { sendOsc(address, *args) }
    }

    fun sendImmediate(address: String, vararg args: Any) {
        sendOsc(address, *args)
    }

    fun sendBatch(messages: List<Pair<String, List<Any>>>) {
        scope.launch {
            for ((address, args) in messages) {
                sendOsc(address, *args.toTypedArray())
            }
            log("Batch sent ${messages.size} messages")
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
        sendOsc(listenAddress, *args)
    }

    fun stopListening(property: String, vararg args: Any) {
        val listenAddress = property.replace("/get/", "/stop_listen/")
        listeners.remove(listenAddress.replace("/stop_listen/", "/start_listen/"))
        sendOsc(listenAddress, *args)
    }

    fun stopAllListeners() {
        listeners.toList().forEach { addr ->
            val stopAddr = addr.replace("/start_listen/", "/stop_listen/")
            sendOsc(stopAddr)
        }
        listeners.clear()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(5000)
                try {
                    sendOsc("/live/test")
                } catch (_: Exception) {
                    _connectionState.value = ConnectionState.ERROR
                }
            }
        }
    }
}
