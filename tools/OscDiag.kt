import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.MessageSelector
import com.illposed.osc.OSCMessageListener
import com.illposed.osc.transport.OSCPortIn
import com.illposed.osc.transport.OSCPortOut
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

fun main(args: Array<String>) {
    println("=== AbletonOSC Diagnostic Tool ===\n")

    // Show local network info
    println("[1] Local network interfaces:")
    for (intf in NetworkInterface.getNetworkInterfaces().asSequence()) {
        if (intf.isLoopback || !intf.isUp) continue
        for (addr in intf.inetAddresses.asSequence()) {
            if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                println("    ${intf.displayName}: ${addr.hostAddress}")
            }
        }
    }
    println()

    if (args.isEmpty()) {
        println("Usage:")
        println("  osc-diag <ip>              Test single host on port 11000")
        println("  osc-diag <ip> <port>       Test single host on custom port")
        println("  osc-diag scan              Scan local subnet for AbletonOSC")
        println("  osc-diag scan <subnet>     Scan specific subnet (e.g. 192.168.1)")
        return
    }

    when (args[0]) {
        "scan" -> {
            val subnet = if (args.size > 1) args[1] else detectSubnet()
            scanSubnet(subnet)
        }
        else -> {
            val host = args[0]
            val port = if (args.size > 1) args[1].toInt() else 11000
            testHost(host, port)
        }
    }
}

fun testHost(host: String, port: Int) {
    println("[2] Testing connectivity to $host:$port ...\n")

    // Step A: DNS / reachability
    print("    DNS resolve: ")
    val addr = try {
        InetAddress.getByName(host).also { println("OK -> ${it.hostAddress}") }
    } catch (e: Exception) {
        println("FAILED - ${e.message}")
        return
    }

    print("    ICMP ping (1s timeout): ")
    val reachable = addr.isReachable(1000)
    println(if (reachable) "OK" else "FAILED (host may block ICMP, continuing anyway)")
    println()

    // Step B: Send /live/test
    println("[3] Sending /live/test to $host:$port ...")
    val remote = InetSocketAddress(addr, port)
    val portOut: OSCPortOut
    try {
        portOut = OSCPortOut(remote)
    } catch (e: Exception) {
        println("    FAILED to create OSCPortOut: ${e.message}")
        return
    }

    // Step C: Listen for replies
    val receivePort = 11001
    println("[4] Listening for replies on port $receivePort ...")
    val portIn: OSCPortIn
    try {
        portIn = OSCPortIn(receivePort)
    } catch (e: Exception) {
        println("    FAILED to bind OSCPortIn on port $receivePort: ${e.message}")
        println("    Trying ephemeral port...")
        try {
            val ephemeral = OSCPortIn(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))
            println("    Bound to ephemeral port (note: AbletonOSC replies to port 11001)")
            startListeningAndSend(ephemeral, portOut, host, port)
            return
        } catch (e2: Exception) {
            println("    FAILED on ephemeral too: ${e2.message}")
            return
        }
    }

    startListeningAndSend(portIn, portOut, host, port)
}

fun startListeningAndSend(portIn: OSCPortIn, portOut: OSCPortOut, host: String, port: Int) {
    @Volatile var gotReply = false

    val selector = object : MessageSelector {
        override fun isInfoRequired() = false
        override fun matches(event: OSCMessageEvent) = true
    }

    portIn.dispatcher.addListener(selector, OSCMessageListener { event ->
        val msg = event.message
        gotReply = true
        println("\n    >> REPLY: ${msg.address} ${msg.arguments}")
    })
    portIn.startListening()

    try {
        portOut.send(OSCMessage("/live/test"))
        println("    Sent /live/test, waiting 5 seconds for reply...")
    } catch (e: Exception) {
        println("    FAILED to send: ${e.message}")
        e.printStackTrace()
        portIn.stopListening()
        portIn.close()
        portOut.close()
        return
    }

    // Also try querying version
    try {
        portOut.send(OSCMessage("/live/application/get/version"))
    } catch (_: Exception) {}

    Thread.sleep(5000)

    portIn.stopListening()
    portIn.close()
    portOut.close()

    println()
    if (gotReply) {
        println("[OK] AbletonOSC is responding at $host:$port!")
    } else {
        println("[FAIL] No reply received. Possible causes:")
        println("  - AbletonOSC is not loaded in Ableton Live on that machine")
        println("  - Wrong IP address (is the server at $host?)")
        println("  - Firewall blocking UDP 11000/11001")
        println("  - The server is on a different subnet/VLAN")
        println()
        println("  Things to check:")
        println("  - In Ableton: Preferences > Link/Tempo/MIDI > Control Surface = AbletonOSC")
        println("  - Ableton status bar should say 'AbletonOSC: Listening for OSC on port 11000'")
        println("  - Try: nc -u $host $port  (then type anything and hit enter)")
    }
}

fun scanSubnet(subnet: String) {
    println("[2] Scanning $subnet.1-254 on port 11000 ...\n")

    val found = mutableListOf<String>()
    val threads = (1..254).map { i ->
        Thread {
            val ip = "$subnet.$i"
            try {
                val addr = InetAddress.getByName(ip)
                if (!addr.isReachable(500)) return@Thread
                print(".")

                val remote = InetSocketAddress(addr, 11000)
                val portOut = OSCPortOut(remote)
                try {
                    portOut.send(OSCMessage("/live/test"))
                    synchronized(found) {
                        found.add(ip)
                        println("\n    FOUND: AbletonOSC responding at $ip")
                    }
                } catch (_: Exception) {
                } finally {
                    portOut.close()
                }
            } catch (_: Exception) {}
        }
    }

    threads.forEach { it.start() }
    threads.forEach { it.join(5000) }

    println("\n")
    if (found.isEmpty()) {
        println("[RESULT] No AbletonOSC instances found on $subnet.*")
        println("  Note: the scan only confirms reachable hosts, not that they replied.")
        println("  Try testing a specific IP directly: osc-diag <ip>")
    } else {
        println("[RESULT] Found ${found.size} instance(s): ${found.joinToString(", ")}")
    }
}

fun detectSubnet(): String {
    for (intf in NetworkInterface.getNetworkInterfaces().asSequence()) {
        if (intf.isLoopback || !intf.isUp) continue
        for (addr in intf.inetAddresses.asSequence()) {
            if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                return addr.hostAddress!!.substringBeforeLast(".")
            }
        }
    }
    return "192.168.1"
}
