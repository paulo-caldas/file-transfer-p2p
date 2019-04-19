package Business;

import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    final static Logger LOGGER = Logger.getLogger(MobileNode.class.getName());
    File sharingDirectory;

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
        this.sharingDirectory = sharingDirectory;
        if (! (sharingDirectory.exists() && sharingDirectory.isDirectory())) {
            throw new IOException("No such directory");
        }

        try {
            NetworkInterface eth0 = NetworkInterface.getNetworkInterfaces().nextElement();
            group = InetAddress.getByName(AddressType.NETWORK_MULTICAST.toString());
            port = Integer.parseInt(AddressType.LISTENING_PORT.toString());

            macAddr = Utils.macByteArrToString(eth0.getHardwareAddress());

            LOGGER.addHandler(new FileHandler(macAddr + "_MobileNodeLog.xml"));
            LOGGER.log(Level.INFO, "Sharing directory: " + sharingDirectory.getCanonicalPath().toString());


            contentRoutingTable = new ContentRoutingTable(this.macAddr);
            contentRoutingTable.recursivePopulateWithLocalContent(sharingDirectory);

            peerKeepaliveTable = new PeerKeepaliveTable("n/a", 3);

            receiveServerSocket = new MulticastSocket(port);
            receiveServerSocket.joinGroup(new InetSocketAddress(group, port), eth0);

            sendServerSocket = new MulticastSocket(port);

            receivePacket = new DatagramPacket(new byte[1024], 1024);

            currentHelloSessionID = 0;
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

            LOGGER.log(Level.INFO, "SENT: " + pdu.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() throws InterruptedException {
        LOGGER.log(Level.INFO, "Starting mobile node process");

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

        LOGGER.log(Level.INFO, "Finishing mobile node process");
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
                LOGGER.log(Level.INFO, "Removed peers: " + removedPeers.toString());
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
        LOGGER.log(Level.INFO, "Listening for peers");
        try {
            while(true) {
                representativeNode.receiveServerSocket.receive(representativeNode.receivePacket);
                byte[] data = representativeNode.receivePacket.getData();

                ByteArrayInputStream in = new ByteArrayInputStream(data);
                ObjectInputStream objectInputStream = new ObjectInputStream(in);

                MobileNetworkPDU pdu = (MobileNetworkPDU) objectInputStream.readObject();
                MobileNetworkMessageType messageType = pdu.getMessageType();
                String peerID = pdu.getSrcMAC();
                String destination = pdu.getDstMAC();

                if (amIPartOfDestination(destination)) {

                    switch (messageType) {
                        case HELLO:
                            boolean isNewEntry;
                            boolean isMyself;

                            synchronized (keepaliveTable) {
                                isNewEntry = !keepaliveTable.hasPeer(peerID);
                            }
                            isMyself = peerID.equals(representativeNode.getMacAddr());
                            if (isNewEntry && !isMyself) {
                                LOGGER.log(Level.INFO, "Received: " + pdu.toString());

                                HelloMobileNetworkPDU helloPDU = (HelloMobileNetworkPDU) pdu;
                                ContentRoutingTable peerContentRoutingTable = helloPDU.getContentRoutingTable();

                                routingTable.mergeWithPeerContentTable(peerContentRoutingTable, pdu.getSrcMAC());

                                synchronized (keepaliveTable) {
                                    keepaliveTable.markAsAlive(peerID);
                                }
                            }
                            break;
                        case PING:
                            LOGGER.log(Level.INFO, "Received: " + pdu.toString());
                            boolean alreadyKnowPeer;
                            synchronized (keepaliveTable) {
                                alreadyKnowPeer = keepaliveTable.hasPeer(peerID);
                            }
                            if (alreadyKnowPeer) {
                                representativeNode.sendPongMessage(pdu.getSrcMAC(), pdu.getSessionID());
                            } else {
                                representativeNode.sendHelloMessage(pdu.getSrcMAC());
                            }
                            break;
                        case PONG:
                            LOGGER.log(Level.INFO, "Received: " + pdu.toString());
                            String sessionID = pdu.getSessionID();
                            boolean isPingRecent;
                            synchronized (keepaliveTable) {
                                isPingRecent = keepaliveTable.markAsAlive(sessionID, peerID);
                            }
                            if (isPingRecent) {
                                LOGGER.log(Level.INFO, "Marked peer " + peerID + " as alive");
                            } else {
                                LOGGER.log(Level.INFO, "Received outdated keepalive from " + peerID);
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
