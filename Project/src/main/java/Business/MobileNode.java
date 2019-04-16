package Business;

import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

import Business.Enum.MobileNetworkErrorType;
import Business.PDU.HelloMobileNetworkPDU;
import Business.PDU.MobileNetworkPDU;

import Business.Enum.MobileNetworkMessageType;
import Business.Enum.AddressType;

/*
 *   ContentRoutingTable
 *
 *   | Content-ID | Node-IDs  | Next hop
 *   | 123        | 999       |  123
 *   | 789        | 777       |  543
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

    private ContentRoutingTable contentRoutingTable;
    private PeerKeepaliveTable<String, String> peerKeepaliveTable;

    public MobileNode(File sharingDirectory) throws IOException{
        this.sharingDirectory = sharingDirectory;
        if (! (sharingDirectory.exists() && sharingDirectory.isDirectory())) {
            throw new IOException("No such directory");
        }


        try {
            NetworkInterface eth0 = NetworkInterface.getByName("eth0");
            InetAddress group = InetAddress.getByName(AddressType.NETWORK_MULTICAST.toString());
            Integer port = Integer.parseInt(AddressType.LISTENING_PORT.toString());

            macAddr = Utils.macByteArrToString(eth0.getHardwareAddress());

            System.out.println("- Sharing directory: " + sharingDirectory.getCanonicalPath());

            this.contentRoutingTable = new ContentRoutingTable(this.macAddr);
            this.contentRoutingTable.recursivePopulateWithLocalContent(sharingDirectory);

            System.out.println(contentRoutingTable.toString());

            receiveServerSocket = new MulticastSocket(port);
            receiveServerSocket.joinGroup(new InetSocketAddress(group, port), eth0);

            sendServerSocket = new MulticastSocket(port);

            outputStream = new ByteArrayOutputStream();
            os = new ObjectOutputStream(outputStream);

            buffer = outputStream.toByteArray();

            receivePacket = new DatagramPacket(new byte[1024], 1024);

            sendPacket = new DatagramPacket(buffer, buffer.length, group, port);
        } catch (IOException |NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public String getMacAddr() {
        return macAddr;
    }


    ContentRoutingTable getContentRoutingTable() {
        return contentRoutingTable;
    }

    PeerKeepaliveTable getPeerKeepaliveTable() {
        return peerKeepaliveTable;
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
                contentRoutingTable);

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
    private PeerKeepaliveTable keepaliveTable;
    private final int KEEPAWAY_TIME_MS = 5000;

    MobileNodeKeepaliveDaemon(MobileNode representativeNode) {
        this.representativeNode = representativeNode;
        this.keepaliveTable = representativeNode.getPeerKeepaliveTable();
    }

    void queryPeers() {
        String timestampOfNow;

        try {
            while (true) {
                timestampOfNow = new Timestamp(Calendar.getInstance().getTime().getTime()).toString();
                keepaliveTable.setCurrentKeepaliveSessionID(timestampOfNow);
                representativeNode.sendHelloMessage(AddressType.LINK_MULTICAST.toString());
                Thread.sleep(KEEPAWAY_TIME_MS);
                keepaliveTable.applyStrikeWave();
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
    private ContentRoutingTable routingTable;
    private PeerKeepaliveTable keepaliveTable;


    MobileNodeListeningDaemon(MobileNode representativeNode) {
        this.representativeNode = representativeNode;
        this.routingTable = representativeNode.getContentRoutingTable();
        this.keepaliveTable = representativeNode.getPeerKeepaliveTable();
    }

    private void listenForPeers() {
        System.out.println("- Listening...");
        try {
            while(true) {
                representativeNode.receiveServerSocket.receive(representativeNode.receivePacket);
                byte[] data = representativeNode.receivePacket.getData();

                ByteArrayInputStream in = new ByteArrayInputStream(data);
                ObjectInputStream objectInputStream = new ObjectInputStream(in);

                MobileNetworkPDU pdu = (MobileNetworkPDU) objectInputStream.readObject();

                MobileNetworkMessageType messageType = pdu.getMessageType();

                String peerID = pdu.getSrcMAC();

                switch (messageType) {
                    case HELLO:
                        if (!keepaliveTable.hasPeer(peerID)) {
                            HelloMobileNetworkPDU helloPDU = (HelloMobileNetworkPDU) pdu;
                            ContentRoutingTable peerContentRoutingTable = helloPDU.getContentRoutingTable();

                            routingTable.mergeWithPeerContentTable(peerContentRoutingTable, pdu.getSrcMAC());
                            keepaliveTable.markAsAlive(peerID);
                        }
                        break;
                    case PING:
                        representativeNode.sendPongMessage(pdu.getDstMAC());
                        break;
                    case PONG:
                        String sessionID = pdu.getSessionID();
                        keepaliveTable.markAsAlive(sessionID, peerID);
                        break;
                    case REQUEST_CONTENT:
                        /**
                         * TODO
                         * if multicast_message
                         *     if have content
                         *         send reply_content
                         *     else
                         *         push_path
                         *         propagate_message
                         * else if direct_message
                         *     next_hop = check_routing_table
                         *          if this == next_hop
                         *              send reply_content
                         *          if neighbour == next_hop
                         *              push_path
                         *              propagate_message
                         *          else
                         *              drop
                         */
                        break;
                    case REPLY_CONTENT:
                        /**
                         * TODO:
                         * next_hop = pop_router_path_and_validate
                         *     if not this == next_hop
                         *         abort
                         *     else if router_path_is_empty
                         *         download_content
                         *         else
                         *             forward_message_to_tail
                         */
                        break;
                    default:
                        // drop
                        break;
                }
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
