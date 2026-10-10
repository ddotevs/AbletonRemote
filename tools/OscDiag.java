import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCMessageEvent;
import com.illposed.osc.MessageSelector;
import com.illposed.osc.OSCMessageListener;
import com.illposed.osc.transport.OSCPortIn;
import com.illposed.osc.transport.OSCPortOut;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class OscDiag {

    public static void main(String[] args) throws Exception {
        System.out.println("=== AbletonOSC Diagnostic Tool ===\n");

        // Show local network info
        System.out.println("[1] Local network interfaces:");
        String detectedSubnet = "192.168.1";
        Enumeration<NetworkInterface> intfs = NetworkInterface.getNetworkInterfaces();
        while (intfs.hasMoreElements()) {
            NetworkInterface intf = intfs.nextElement();
            if (intf.isLoopback() || !intf.isUp()) continue;
            Enumeration<InetAddress> addrs = intf.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = addrs.nextElement();
                if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                    System.out.println("    " + intf.getDisplayName() + ": " + a.getHostAddress());
                    String ip = a.getHostAddress();
                    detectedSubnet = ip.substring(0, ip.lastIndexOf('.'));
                }
            }
        }
        System.out.println();

        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  osc-diag <ip>              Test single host on port 11000");
            System.out.println("  osc-diag <ip> <port>       Test single host on custom port");
            System.out.println("  osc-diag scan              Scan local subnet");
            System.out.println("  osc-diag scan <subnet>     Scan specific subnet (e.g. 192.168.1)");
            return;
        }

        if ("scan".equals(args[0])) {
            String subnet = args.length > 1 ? args[1] : detectedSubnet;
            scanSubnet(subnet);
        } else {
            String host = args[0];
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 11000;
            testHost(host, port);
        }
    }

    static void testHost(String host, int port) throws Exception {
        System.out.println("[2] Testing connectivity to " + host + ":" + port + " ...\n");

        // DNS
        System.out.print("    DNS resolve: ");
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
            System.out.println("OK -> " + addr.getHostAddress());
        } catch (Exception e) {
            System.out.println("FAILED - " + e.getMessage());
            return;
        }

        // Ping
        System.out.print("    ICMP ping (1s): ");
        boolean reachable = addr.isReachable(1000);
        System.out.println(reachable ? "OK" : "FAILED (host may block ICMP, continuing)");

        // Raw UDP test first (no JavaOSC, just a plain datagram)
        System.out.println("\n[3] Raw UDP test to " + host + ":" + port + " ...");
        try {
            DatagramSocket rawSocket = new DatagramSocket();
            rawSocket.setSoTimeout(3000);
            byte[] payload = "/live/test\0\0,\0\0\0".getBytes("ISO-8859-1");
            // Fix: proper OSC message = address + padding + type tag
            // Address: /live/test (10 bytes, pad to 12)
            // Type tag: , (2 bytes, pad to 4)
            byte[] oscMsg = new byte[16];
            byte[] addrBytes = "/live/test".getBytes("US-ASCII");
            System.arraycopy(addrBytes, 0, oscMsg, 0, addrBytes.length);
            // bytes 10,11 = 0 (null terminator + padding to 4-byte boundary)
            oscMsg[12] = ',';
            // bytes 13,14,15 = 0 (null terminator + padding)
            
            DatagramPacket sendPkt = new DatagramPacket(oscMsg, oscMsg.length, addr, port);
            rawSocket.send(sendPkt);
            System.out.println("    Sent raw UDP OSC packet (" + oscMsg.length + " bytes)");
            
            // Try to receive
            byte[] buf = new byte[1024];
            DatagramPacket recvPkt = new DatagramPacket(buf, buf.length);
            try {
                rawSocket.receive(recvPkt);
                System.out.println("    >> RAW REPLY from " + recvPkt.getAddress() + ":" + recvPkt.getPort() + 
                    " (" + recvPkt.getLength() + " bytes)");
                System.out.println("    >> Data: " + new String(buf, 0, Math.min(recvPkt.getLength(), 100), "US-ASCII"));
            } catch (SocketTimeoutException e) {
                System.out.println("    No raw UDP reply within 3s");
            }
            rawSocket.close();
        } catch (Exception e) {
            System.out.println("    Raw UDP failed: " + e.getMessage());
        }

        // JavaOSC test
        System.out.println("\n[4] JavaOSC library test ...");
        InetSocketAddress remote = new InetSocketAddress(addr, port);
        OSCPortOut portOut;
        try {
            portOut = new OSCPortOut(remote);
            System.out.println("    OSCPortOut created OK");
        } catch (Exception e) {
            System.out.println("    FAILED to create OSCPortOut: " + e.getMessage());
            return;
        }

        int recvPort = 11001;
        System.out.println("    Binding OSCPortIn to port " + recvPort + " ...");
        OSCPortIn portIn;
        try {
            portIn = new OSCPortIn(recvPort);
            System.out.println("    OSCPortIn bound OK");
        } catch (Exception e) {
            System.out.println("    FAILED to bind port " + recvPort + ": " + e.getMessage());
            System.out.println("    Trying 0.0.0.0:" + recvPort + " ...");
            try {
                portIn = new OSCPortIn(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), recvPort));
                System.out.println("    OSCPortIn bound OK (wildcard)");
            } catch (Exception e2) {
                System.out.println("    FAILED: " + e2.getMessage());
                portOut.close();
                return;
            }
        }

        AtomicBoolean gotReply = new AtomicBoolean(false);
        MessageSelector selector = new MessageSelector() {
            public boolean isInfoRequired() { return false; }
            public boolean matches(OSCMessageEvent event) { return true; }
        };

        portIn.getDispatcher().addListener(selector, new OSCMessageListener() {
            public void acceptMessage(OSCMessageEvent event) {
                gotReply.set(true);
                System.out.println("\n    >> OSC REPLY: " + event.getMessage().getAddress() + 
                    " " + event.getMessage().getArguments());
            }
        });
        portIn.startListening();
        System.out.println("    Listening...");

        try {
            portOut.send(new OSCMessage("/live/test"));
            System.out.println("    Sent /live/test via JavaOSC");
            portOut.send(new OSCMessage("/live/application/get/version"));
            System.out.println("    Sent /live/application/get/version");
        } catch (Exception e) {
            System.out.println("    FAILED to send: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("    Waiting 5 seconds for replies...");
        Thread.sleep(5000);

        portIn.stopListening();
        portIn.close();
        portOut.close();

        System.out.println();
        if (gotReply.get()) {
            System.out.println("[OK] AbletonOSC is responding at " + host + ":" + port + "!");
        } else {
            System.out.println("[FAIL] No reply received.");
            System.out.println("  Possible causes:");
            System.out.println("  - AbletonOSC not loaded (Preferences > Link/Tempo/MIDI > Control Surface = AbletonOSC)");
            System.out.println("  - Wrong IP (is Ableton at " + host + "?)");
            System.out.println("  - Firewall blocking UDP 11000/11001");
            System.out.println("  - Different subnet/VLAN");
        }
    }

    static void scanSubnet(String subnet) throws Exception {
        System.out.println("[2] Scanning " + subnet + ".1-254 on port 11000 ...\n");
        List<String> found = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();

        for (int i = 1; i <= 254; i++) {
            final String ip = subnet + "." + i;
            Thread t = new Thread(() -> {
                try {
                    InetAddress addr = InetAddress.getByName(ip);
                    if (!addr.isReachable(800)) return;
                    System.out.print("*");
                    System.out.flush();
                    
                    // Raw UDP probe
                    DatagramSocket sock = new DatagramSocket();
                    sock.setSoTimeout(2000);
                    byte[] oscMsg = new byte[16];
                    byte[] addrBytes = "/live/test".getBytes("US-ASCII");
                    System.arraycopy(addrBytes, 0, oscMsg, 0, addrBytes.length);
                    oscMsg[12] = ',';
                    
                    DatagramPacket pkt = new DatagramPacket(oscMsg, oscMsg.length, addr, 11000);
                    sock.send(pkt);
                    
                    byte[] buf = new byte[256];
                    DatagramPacket recv = new DatagramPacket(buf, buf.length);
                    try {
                        sock.receive(recv);
                        found.add(ip);
                        System.out.println("\n    FOUND: " + ip + " replied!");
                    } catch (SocketTimeoutException e) {
                        // no reply
                    }
                    sock.close();
                } catch (Exception e) {
                    // skip
                }
            });
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) {
            t.join(5000);
        }

        System.out.println("\n");
        if (found.isEmpty()) {
            System.out.println("[RESULT] No AbletonOSC instances found on " + subnet + ".*");
        } else {
            System.out.println("[RESULT] Found " + found.size() + " instance(s): " + String.join(", ", found));
        }
    }
}
