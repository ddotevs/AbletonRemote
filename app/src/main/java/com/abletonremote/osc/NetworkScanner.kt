package com.abletonremote.osc

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

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
        // Single socket bound to 11001 — send probes from here,
        // AbletonOSC replies to port 11001 on the sender's IP.
        val sock: DatagramSocket
        try {
            sock = DatagramSocket(null)
            sock.reuseAddress = true
            sock.bind(InetSocketAddress("0.0.0.0", 11001))
        } catch (_: Exception) {
            // Port 11001 already in use (active connection) — can't scan while connected
            onFound(DiscoveredServer(
                host = "",
                name = "Disconnect first to scan (port 11001 in use)"
            ))
            return@withContext
        }
        sock.soTimeout = 500

        val oscMsg = buildOscTestMessage()
        val found = mutableSetOf<String>()

        // Send probes to all IPs on the subnet
        for (i in 1..254) {
            try {
                val addr = InetAddress.getByName("$subnet.$i")
                val pkt = DatagramPacket(oscMsg, oscMsg.size, addr, port)
                sock.send(pkt)
            } catch (_: Exception) {}
        }

        // Now listen for replies
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(256)
        while (System.currentTimeMillis() < deadline) {
            try {
                val recv = DatagramPacket(buf, buf.size)
                sock.receive(recv)
                val ip = recv.address.hostAddress ?: continue
                if (ip !in found) {
                    val response = String(buf, 0, recv.length, Charsets.US_ASCII)
                    if (response.contains("/live/test") || response.contains("ok")) {
                        found.add(ip)
                        onFound(DiscoveredServer(host = ip, name = "Ableton @ $ip"))
                    }
                }
            } catch (_: java.net.SocketTimeoutException) {
                // Keep waiting until deadline
            } catch (_: Exception) {
                break
            }
        }

        sock.close()
    }

    private fun buildOscTestMessage(): ByteArray {
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
