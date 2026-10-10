package com.abletonremote.osc

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

data class DiscoveredServer(
    val host: String,
    val name: String
)

object NetworkScanner {

    suspend fun scanForAbleton(
        subnet: String = detectSubnet(),
        port: Int = 11000,
        timeoutMs: Long = 5000,
        onFound: (DiscoveredServer) -> Unit
    ) = withContext(Dispatchers.IO) {
        // OSC message: /live/test with no args
        val oscMsg = buildOscTestMessage()

        val jobs = (1..254).map { i ->
            async {
                val ip = "$subnet.$i"
                try {
                    val addr = InetAddress.getByName(ip)
                    val sock = DatagramSocket()
                    sock.soTimeout = 2000

                    val pkt = DatagramPacket(oscMsg, oscMsg.size, addr, port)
                    sock.send(pkt)

                    // Wait for a reply — only report if we actually get one
                    val buf = ByteArray(256)
                    val recv = DatagramPacket(buf, buf.size)
                    try {
                        sock.receive(recv)
                        val response = String(buf, 0, recv.length, Charsets.US_ASCII)
                        if (response.contains("/live/test") || response.contains("ok")) {
                            onFound(DiscoveredServer(host = ip, name = "Ableton @ $ip"))
                        }
                    } catch (_: SocketTimeoutException) {
                        // No reply — not AbletonOSC
                    }
                    sock.close()
                } catch (_: Exception) {}
            }
        }

        withTimeoutOrNull(timeoutMs) {
            jobs.awaitAll()
        }
        jobs.forEach { it.cancel() }
    }

    private fun buildOscTestMessage(): ByteArray {
        // /live/test\0\0 (12 bytes, padded to 4-byte boundary)
        // ,\0\0\0 (4 bytes type tag)
        val msg = ByteArray(16)
        val addr = "/live/test".toByteArray(Charsets.US_ASCII)
        System.arraycopy(addr, 0, msg, 0, addr.size)
        msg[12] = ','.code.toByte()
        return msg
    }

    private fun detectSubnet(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces.asSequence()) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses.asSequence()) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        return ip.substringBeforeLast(".")
                    }
                }
            }
            "192.168.1"
        } catch (_: Exception) {
            "192.168.1"
        }
    }
}
