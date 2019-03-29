import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 *
 *   | Content-ID |   Node-IDs
 *   | 123        |   999,888
 *   | 789        |   777
 *
 */

public class MobileNode {
    DatagramSocket serverSocket;
    DatagramPacket receivePacket;
    DatagramPacket sendPacket;
    byte[] buffer;
    private String macAddr;
    private Map<String,List<String>> contentTable; // Maps content IDs to the nodes that provide them

    public MobileNode() {
        try {
            this.macAddr = macByteArrToString(NetworkInterface.getNetworkInterfaces().nextElement().getHardwareAddress());
            this.serverSocket = new DatagramSocket(Integer.parseInt(AddressType.LISTENING_PORT.toString()));
            buffer = new byte[1024];
            this.contentTable = new HashMap<>();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public MobileNode(String macAddr, Map<String,List<String>> contentTable) {
        this.macAddr = macAddr;
        this.contentTable = contentTable;
    }

    private String macByteArrToString(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i],
                    (i < mac.length - 1) ? ":" : ""));
        }

        return sb.toString();
    }

    public void sendHelloMessage(String dstMac) {
        try {
            buffer = new String("hello from " + this.macAddr + " to " + dstMac).getBytes();
            sendPacket = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(AddressType.NETWORK_BROADCAST.toString()), Integer.parseInt(AddressType.LISTENING_PORT.toString()));
            serverSocket.send(sendPacket);
        } catch (IOException e) {
          }
    }

    public void sendPingMessage(String dstMac) {
        try {
            buffer = new String("ping from " + this.macAddr + " to " + dstMac).getBytes();
            sendPacket = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(AddressType.NETWORK_BROADCAST.toString()), Integer.parseInt(AddressType.LISTENING_PORT.toString()));
            serverSocket.send(sendPacket);
        } catch (IOException e) {
        }
    }

    public void sendPongMessage(String dstMac) {
        try {
            buffer = new String("pong from " + this.macAddr + " to " + dstMac).getBytes();
            sendPacket = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(AddressType.NETWORK_BROADCAST.toString()), Integer.parseInt(AddressType.LISTENING_PORT.toString()));
            serverSocket.send(sendPacket);
        } catch (IOException e) {
        }
    }

    // Query if my peers are alive or not by sending pings and awaiting for pongs
    public void queryPeers() {

    }

    public void addPeer(String peerMAC) {

    }

    public void listenForPeers() {
        try {
            while(true) {
                serverSocket.receive(receivePacket);
                String data = new String(receivePacket.getData());

                System.out.println(data);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        sendHelloMessage(AddressType.LINK_MULTICAST.toString());
        new MobileNodeKeepaliveDaemon(this).run();
        listenForPeers();
    }
}
