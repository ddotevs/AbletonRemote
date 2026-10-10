package com.abletonremote.osc

import com.illposed.osc.OSCMessage
import com.illposed.osc.transport.OSCPortOut
import kotlinx.coroutines.*
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
        timeoutMs: Long = 3000,
        onFound: (DiscoveredServer) -> Unit
    ) = withContext(Dispatchers.IO) {
        val jobs = (1..254).map { i ->
            async {
                val ip = "$subnet.$i"
                try {
                    val addr = InetAddress.getByName(ip)
                    if (!addr.isReachable(500)) return@async

                    val remote = InetSocketAddress(addr, port)
                    val builder = OscConnection.createAndroidSafeBuilder()
                    val portOut = OSCPortOut(builder, remote)
                    try {
                        portOut.send(OSCMessage("/live/test"))
                        onFound(DiscoveredServer(host = ip, name = "Ableton @ $ip"))
                    } catch (_: Exception) {
                    } finally {
                        try { portOut.close() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
        }
        // Wait for all probes or timeout
        withTimeoutOrNull(timeoutMs) {
            jobs.awaitAll()
        }
        // Cancel any still-running probes
        jobs.forEach { it.cancel() }
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
