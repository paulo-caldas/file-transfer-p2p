package Business;

import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;

import Business.Enum.MobileNetworkErrorType;
import Business.PDU.HelloMobileNetworkPDU;
import Business.PDU.MobileNetworkPDU;

import Business.Enum.MobileNetworkMessageType;
import Business.Enum.AddressType;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.xml.DOMConfigurator;

/*
 *   ContentRoutingTable
 *
 *   | Content-ID | Node-IDs  | Next hop
 *   | 123        | 999       |  123
 *   | 789        | 777       |  543
 *
 */

public class MobileNode {
    final static Logger LOGGER = Logger.getLogger(MobileNode.class);

    MulticastSocket receiveServerSocket;
    MulticastSocket sendServerSocket;

    DatagramPacket receivePacket;
    DatagramPacket sendPacket;

    ByteArrayOutputStream outputStream;
    ObjectOutputStream os;

    byte[] buffer;

    private String macAddr;
    InetAddress group;
    Integer port;

    private ContentRoutingTable contentRoutingTable;
    private PeerKeepaliveTable<String, String> peerKeepaliveTable;

    private Integer currentHelloSessionID;

    public MobileNode(File sharingDirectory) throws IOException{
        if (! (sharingDirectory.exists() && sharingDirectory.isDirectory())) {
            throw new IOException("No such directory");
        }

        try {
            // Setting up needed instance variables
            NetworkInterface eth0 = NetworkInterface.getByName("eth0");
            group = InetAddress.getByName(AddressType.NETWORK_MULTICAST.toString());
            port = Integer.parseInt(AddressType.LISTENING_PORT.toString());
            macAddr = Utils.macByteArrToString(eth0.getHardwareAddress());
            currentHelloSessionID = 0;

            // Setting up logger
            System.setProperty("logfile.name", macAddr + "_MobileNodeLog.xml");
            DOMConfigurator.configure("log4j.xml");

            LOGGER.info("Sharing directory: " + sharingDirectory.getCanonicalPath());

            // Populating content sharing table with all files inside the sharind directory folder passed by argument
            contentRoutingTable = new ContentRoutingTable(this.macAddr);
            List<File> filesInPath = Utils.getFilesInsidePath(sharingDirectory);
            filesInPath.forEach(( file ->  {
                LOGGER.info("Indexing file: " + file.getName());
                try {
                    contentRoutingTable.addOwnedReference(file);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } ));

            // Setting up the table to help keep count of what peers did not prove they're alive
            peerKeepaliveTable = new PeerKeepaliveTable("n/a", 3);

            // Setting up sockets and packets, needed for UDP communication
            receiveServerSocket = new MulticastSocket(port);
            receiveServerSocket.joinGroup(new InetSocketAddress(group, port), eth0);
            sendServerSocket = new MulticastSocket(port);
            receivePacket = new DatagramPacket(new byte[1024], 1024);
        } catch (Exception e) {
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
        MobileNetworkPDU helloPacket = new HelloMobileNetworkPDU(
                macAddr,
                dstMac,
                MobileNetworkMessageType.HELLO,
                MobileNetworkErrorType.VALID,
                62,
                String.valueOf(currentHelloSessionID),
                contentRoutingTable);
        sendPDU(helloPacket);
        currentHelloSessionID++;
    }

    protected void sendPingMessage(String dstMac, String sessionID) {
        MobileNetworkPDU pingPacket = new MobileNetworkPDU(
                macAddr,
                dstMac,
                MobileNetworkMessageType.PING,
                MobileNetworkErrorType.VALID,
                62,
                sessionID);
        sendPDU(pingPacket);
    }

    protected void sendPongMessage(String dstMac, String sessionID) {
        MobileNetworkPDU pongPacket = new MobileNetworkPDU(
                macAddr,
                dstMac,
                MobileNetworkMessageType.PONG,
                MobileNetworkErrorType.VALID,
                62,
                sessionID);
        sendPDU(pongPacket);
    }

    protected void sendPDU(MobileNetworkPDU pdu) {
        try {
            outputStream = new ByteArrayOutputStream();
            os = new ObjectOutputStream(outputStream);

            os.writeObject(pdu);
            os.flush();

            buffer = outputStream.toByteArray();

            sendPacket = new DatagramPacket(buffer, buffer.length, group, port);
            sendPacket.setData(buffer);
            sendServerSocket.send(sendPacket);

            LOGGER.debug("Sent: " + pdu.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() throws InterruptedException {
        LOGGER.debug("Starting mobile node process");

        // Initialize into the network by announcing everyone your presence
        sendHelloMessage(AddressType.LINK_MULTICAST.toString());

        // Listens for incoming messages
        Thread listeningDaemon = new Thread(new MobileNodeListeningDaemon(this));

        // Periodically query peers if they're alive
        Thread queryingDaemon = new Thread(new MobileNodeKeepaliveDaemon(this));

        listeningDaemon.start();
        queryingDaemon.start();

        listeningDaemon.join();
        queryingDaemon.join();

        LOGGER.debug("Finishing mobile node process");
    }

    public Logger getLogger() {
        return LOGGER;
    }
}

class MobileNodeKeepaliveDaemon extends Thread{
    private MobileNode representativeNode;
    private PeerKeepaliveTable keepaliveTable;
    private final int KEEPAWAY_TIME_MS = 5000;
    private Logger LOGGER;

    MobileNodeKeepaliveDaemon(MobileNode representativeNode) {
        this.representativeNode = representativeNode;
        this.keepaliveTable = representativeNode.getPeerKeepaliveTable();
        this.LOGGER = representativeNode.getLogger();
    }

    void queryPeers() {

        try {
            while (true) {
                String timestampOfNow = new Timestamp(Calendar.getInstance().getTime().getTime()).toString();
                synchronized (keepaliveTable) {
                    keepaliveTable.setCurrentKeepaliveSessionID(timestampOfNow);
                    keepaliveTable.getPeers().forEach(peer -> representativeNode.sendPingMessage((String) peer, timestampOfNow));
                }
                Thread.sleep(KEEPAWAY_TIME_MS);
                List<String> removedPeers;
                synchronized (keepaliveTable) {
                    removedPeers = keepaliveTable.applyStrikeWave();
                }
                LOGGER.info("Removed peers: " + removedPeers.toString());
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
    private Logger LOGGER;


    MobileNodeListeningDaemon(MobileNode representativeNode) {
        this.representativeNode = representativeNode;
        this.routingTable = representativeNode.getContentRoutingTable();
        this.keepaliveTable = representativeNode.getPeerKeepaliveTable();
        this.LOGGER = representativeNode.getLogger();
    }

    private void listenForPeers() {
        LOGGER.debug("Listening for peers");
        try {
            while(true) {
                // Await for a connection to be made, and parse it as a PDU object
                representativeNode.receiveServerSocket.receive(representativeNode.receivePacket);
                byte[] data = representativeNode.receivePacket.getData();
                ByteArrayInputStream in = new ByteArrayInputStream(data);
                ObjectInputStream objectInputStream = new ObjectInputStream(in);
                MobileNetworkPDU pdu = (MobileNetworkPDU) objectInputStream.readObject();

                // Retrieve important variables that dictate what to do next
                MobileNetworkMessageType messageType = pdu.getMessageType();
                String peerID = pdu.getSrcMAC();
                String destination = pdu.getDstMAC();

                // Only proceed if the message is directed towards me
                if (amIPartOfDestination(destination)) {

                    switch (messageType) {
                        case HELLO:
                            boolean isNewEntry;
                            boolean isMyself;

                            isMyself = peerID.equals(representativeNode.getMacAddr());
                            synchronized (keepaliveTable) { isNewEntry = !keepaliveTable.hasPeer(peerID); }

                            if (isNewEntry && !isMyself) {
                                LOGGER.debug("Received: " + pdu.toString());

                                HelloMobileNetworkPDU helloPDU = (HelloMobileNetworkPDU) pdu;
                                ContentRoutingTable peerContentRoutingTable = helloPDU.getContentRoutingTable();

                                peerContentRoutingTable.values().forEach(
                                        tableEntry ->
                                                routingTable.addReference(
                                                        tableEntry.getFileHash(),
                                                        tableEntry.getDstMAC(),
                                                        peerID,
                                                        1 + tableEntry.getHopCount()));
                                synchronized (keepaliveTable) { keepaliveTable.markAsAlive(peerID); }
                            }
                            break;
                        case PING:
                            LOGGER.debug("Received: " + pdu.toString());

                            boolean alreadyKnowPeer;
                            synchronized (keepaliveTable) { alreadyKnowPeer = keepaliveTable.hasPeer(peerID); }

                            if (alreadyKnowPeer) {
                                representativeNode.sendPongMessage(pdu.getSrcMAC(), pdu.getSessionID());
                            } else {
                                synchronized (keepaliveTable) { keepaliveTable.markAsAlive(peerID); }
                                representativeNode.sendHelloMessage(pdu.getSrcMAC());
                            }
                            break;
                        case PONG:
                            LOGGER.debug("Received: " + pdu.toString());

                            String sessionID = pdu.getSessionID();
                            boolean isPingRecent;
                            synchronized (keepaliveTable) { isPingRecent = keepaliveTable.markAsAlive(sessionID, peerID); }
                            if (isPingRecent) {
                                LOGGER.debug("Marked peer " + peerID + " as alive");
                            } else {
                                LOGGER.debug("Received outdated keepalive from " + peerID);
                            }
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
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private boolean amIPartOfDestination(String destination) {
        return (destination.equals(representativeNode.getMacAddr())
                || destination.equals(AddressType.LINK_BROADCAST.toString())
                || destination.equals(AddressType.LINK_MULTICAST.toString()));
    }

    @Override
    public void run() {
        System.out.println("- Starting listening daemon");
        listenForPeers();
    }
}
