package Business;

import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import Business.Enum.*;
import Business.PDU.HelloMobileNetworkPDU;
import Business.PDU.MobileNetworkPDU;

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

    private Map<String, MobileRoutingTableEntry> contentRoutingTable;

    public MobileNode(File sharingDirectory) throws IOException{
        this.sharingDirectory = sharingDirectory;
        if (! (sharingDirectory.exists() && sharingDirectory.isDirectory())) {
            throw new IOException("No such directory");
        }

        System.out.println("- Sharing directory: " + sharingDirectory.getCanonicalPath());

        initContentTable(sharingDirectory);

        System.out.println(contentRoutingTable.toString());

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

    public Map<String, MobileRoutingTableEntry> getContentRoutingTable() {
        return contentRoutingTable;
    }

    private void initContentTable(File startingFile) {
        for (File file : startingFile.listFiles()) {
            if (file.isFile()) {

                try {
                    String fileHash = Utils.hashFile(file, "md5");
                    contentRoutingTable.put( fileHash, new MobileRoutingTableEntry(fileHash, macAddr, null, 0));
                    System.out.println("- Hashed " + file + ":" + fileHash);
                } catch (IOException | NoSuchAlgorithmException e) {}
            } else {
                initContentTable(file);
            }
        }
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
                // TODO: update peers as dead
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

                MobileNetworkMessageType messageType = pdu.getMessageType();

                switch (messageType) {
                    case HELLO:
                        HelloMobileNetworkPDU helloPDU = (HelloMobileNetworkPDU) pdu;
                        Map<String, MobileRoutingTableEntry> peerContentRoutingTable = helloPDU.getContentRoutingTable();
                        for (Map.Entry<String,MobileRoutingTableEntry> tableEntry : peerContentRoutingTable.entrySet()) {
                            representativeNode.getContentRoutingTable().put(
                                    tableEntry.getKey(),
                                    new MobileRoutingTableEntry(
                                            tableEntry.getValue().getFileHash(),
                                            tableEntry.getValue().getDstMAC(),
                                            pdu.getSrcMAC(),
                                            1 + tableEntry.getValue().getHopCount())
                                    );
                        }
                        break;
                    case PING:
                        representativeNode.sendPongMessage(pdu.getDstMAC());
                        break;
                    case PONG:
                        // TODO: Update peer as alive
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
