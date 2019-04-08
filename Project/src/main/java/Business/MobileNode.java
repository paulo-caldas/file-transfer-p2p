package main.java;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import main.java.Enum.*;
import main.java.PDU.HelloMobileNetworkPDU;
import main.java.PDU.MobileNetworkPDU;

import org.apache.commons.codec.digest.DigestUtils;

/*
 *
 *   | Content-ID |   Node-IDs
 *   | 123        |   999,888
 *   | 789        |   777
 *
 */

public class MobileNode {
    public File sharingDirectory;

    MulticastSocket receiveServerSocket;
    MulticastSocket sendServerSocket;

    DatagramPacket receivePacket;
    DatagramPacket sendPacket;

    ByteArrayOutputStream outputStream;
    ObjectOutputStream os;

    byte[] buffer;
    private String macAddr;
    private Map<String,List<ContentReference>> contentTable;

    public MobileNode(File sharingDirectory) throws IOException{
        this.sharingDirectory = sharingDirectory;
        if (! (sharingDirectory.exists() && sharingDirectory.isDirectory())) {
            throw new IOException("No such directory");
        }

        System.out.println("- Sharing directory: " + sharingDirectory.getCanonicalPath());

        initContentTable(sharingDirectory);

        try {
            NetworkInterface eth0 = NetworkInterface.getByName("eth0");
            InetAddress group = InetAddress.getByName(AddressType.NETWORK_MULTICAST.toString());
            Integer port = Integer.parseInt(AddressType.LISTENING_PORT.toString());

            macAddr = Utils.macByteArrToString(eth0.getHardwareAddress());

            receiveServerSocket = new MulticastSocket(port);
            receiveServerSocket.joinGroup(new InetSocketAddress(group, port), eth0);

            sendServerSocket = new MulticastSocket(port);

            outputStream = new ByteArrayOutputStream();
            os = new ObjectOutputStream(outputStream);

            buffer = outputStream.toByteArray();

            receivePacket = new DatagramPacket(new byte[1024], 1024);

            sendPacket = new DatagramPacket(buffer, buffer.length, group, port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initContentTable(File startingFile) {
        for (File file : startingFile.listFiles()) {
            if (file.isFile()) {

                try {
                    String hash = hashfile(file);

                    System.out.println("- Hashed " + file + ":" + hash);
                } catch (IOException e) {}
            } else {
                initContentTable(file);
            }
        }
    }

    private String hashfile(File file) throws IOException {
        InputStream is = Files.newInputStream(file.toPath());
        String md5 = DigestUtils.md5Hex(is);

        return md5;
    }

    protected void sendHelloMessage(String dstMac) {
        System.out.println("- Sending hello message to " + dstMac);
        MobileNetworkPDU helloPacket = new HelloMobileNetworkPDU(
                macAddr,
                dstMac,
                MobileNetworkMessageType.HELLO,
                MobileNetworkErrorType.VALID,
                62,
                "0",
                contentTable);

        sendPDU(helloPacket);
    }

    protected void sendPingMessage(String dstMac) {
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

    protected void sendPongMessage(String dstMac) {
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

    protected void sendPDU(MobileNetworkPDU pdu) {
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


    public void run() throws InterruptedException {
        System.out.println("- Starting mobile node");

        // Listens for incoming messages
        Thread listeningDaemon = new Thread(new MobileNodeListeningDaemon(this));

        // Periodically queries peers if they're alive
        Thread queryingDaemon = new Thread(new MobileNodeKeepaliveDaemon(this));

        listeningDaemon.start();
        queryingDaemon.start();

        listeningDaemon.join();
        queryingDaemon.join();

        System.out.println("- Finishing mobile node");
    }
}

class MobileNodeKeepaliveDaemon extends Thread{
    private MobileNode representativeNode;

    public MobileNodeKeepaliveDaemon(MobileNode representativeNode) {
        this.representativeNode = representativeNode;
    }

    public void queryPeers() {
        try {
            while (true) {
                representativeNode.sendHelloMessage(AddressType.LINK_MULTICAST.toString());
                Thread.sleep(5000);
                representativeNode.queryPeers();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run () {
        System.out.println("- Starting keepalive daemon");
        queryPeers();
    }
}

class MobileNodeListeningDaemon extends Thread{
    private MobileNode representativeNode;

    public MobileNodeListeningDaemon(MobileNode representativeNode) {
        this.representativeNode = representativeNode;
    }

    public void listenForPeers() {
        System.out.println("- Listening...");
        try {
            while(true) {
                representativeNode.receiveServerSocket.receive(representativeNode.receivePacket);
                byte[] data = representativeNode.receivePacket.getData();

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

    @Override
    public void run() {
        System.out.println("- Starting listening daemon");
        listenForPeers();
    }
}
