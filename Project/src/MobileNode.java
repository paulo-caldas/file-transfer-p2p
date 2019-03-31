import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Enum.*;
import PDU.MobileNetworkPDU;

/*
 *
 *   | Content-ID |   Node-IDs
 *   | 123        |   999,888
 *   | 789        |   777
 *
 */

public class MobileNode {
    MulticastSocket receiveServerSocket;
    MulticastSocket sendServerSocket;

    DatagramPacket receivePacket;
    DatagramPacket sendPacket;

    ByteArrayOutputStream outputStream;
    ObjectOutputStream os;

    byte[] buffer;
    private String macAddr;
    private Map<String,List<String>> contentTable; // Maps content IDs to the nodes that provide them

    public MobileNode() {
        try {
            NetworkInterface eth0 = NetworkInterface.getByName("eth0");
            InetAddress group = InetAddress.getByName(AddressType.NETWORK_MULTICAST.toString());
            Integer port = Integer.parseInt(AddressType.LISTENING_PORT.toString());

            macAddr = macByteArrToString(eth0.getHardwareAddress());

            receiveServerSocket = new MulticastSocket(port);
            receiveServerSocket.joinGroup(new InetSocketAddress(group, port), eth0);

            sendServerSocket = new MulticastSocket(port);

            outputStream = new ByteArrayOutputStream();
            os = new ObjectOutputStream(outputStream);

            buffer = outputStream.toByteArray();

            receivePacket = new DatagramPacket(new byte[1024], 1024);

            sendPacket = new DatagramPacket(buffer, buffer.length, group, port);

            contentTable = new HashMap<>();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        System.out.println("- Sending hello message to " + dstMac);
        MobileNetworkPDU helloPacket = new MobileNetworkPDU(
                macAddr,
                dstMac,
                MobileNetworkMessageType.HELLO,
                MobileNetworkErrorType.VALID,
                62,
                "0");

        sendPDU(helloPacket);
    }

    public void sendPingMessage(String dstMac) {
        System.out.println("- Sending hello message to " + dstMac);
        MobileNetworkPDU pingPacket = new MobileNetworkPDU(
                macAddr,
                dstMac,
                MobileNetworkMessageType.PING,
                MobileNetworkErrorType.VALID,
                62,
                "0");

        sendPDU(pingPacket);

    }

    public void sendPongMessage(String dstMac) {
        System.out.println("- Sending pong message " + dstMac);
        MobileNetworkPDU pongPacket = new MobileNetworkPDU(
                macAddr,
                dstMac,
                MobileNetworkMessageType.PONG,
                MobileNetworkErrorType.VALID,
                62,
                "0");

        sendPDU(pongPacket);
    }

    public void sendPDU(MobileNetworkPDU pdu) {
        try {
            os.writeObject(pdu);

            buffer = outputStream.toByteArray();
            sendPacket.setData(buffer);
            sendServerSocket.send(sendPacket);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Query if my peers are alive or not by sending pings and awaiting for pongs
    public void queryPeers() {

    }

    public void addPeer(String peerMAC) {

    }

    public void listenForPeers() {
        System.out.println("- Listening...");
        try {
            while(true) {
                receiveServerSocket.receive(receivePacket);
                byte[] data = receivePacket.getData();

                ByteArrayInputStream in = new ByteArrayInputStream(data);
                ObjectInputStream objectInputStream = new ObjectInputStream(in);

                MobileNetworkPDU pdu = (MobileNetworkPDU) objectInputStream.readObject();

                System.out.println("Got: " + pdu.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        System.out.println("- Starting mobile node");
        sendHelloMessage(AddressType.LINK_MULTICAST.toString());
        new Thread(new MobileNodeKeepaliveDaemon(this)).start();
        listenForPeers();
    }
}
