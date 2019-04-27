package Business;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Scanner;

import Business.MobileNetworkNode.ContentRoutingTable;
import Business.MobileNetworkNode.Daemon.MobileNodeKeepaliveDaemon;
import Business.MobileNetworkNode.Daemon.MobileNodeListeningDaemon;
import Business.MobileNetworkNode.PeerKeepaliveTable;
import Business.PDU.HelloMobileNetworkPDU;
import Business.PDU.MobileNetworkPDU;

import View.MainView;
import View.Utilities.Menu;
import org.apache.log4j.Logger;
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
    public enum AddressType {
        LINK_BROADCAST("FF:FF:FF:FF:FF:FF"),
        LINK_MULTICAST("FF:FF:FF:FF:FF:FF"),
        NETWORK_BROADCAST("10.0.0.255"),
        NETWORK_MULTICAST("239.255.42.99"),
        LISTENING_PORT("6789");

        private final String text;

        /**
         * @param text
         */
        AddressType(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

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

    public void autonamousStart() throws InterruptedException {
        LOGGER.debug("Starting mobile node process");

        // Initialize into the network by announcing everyone your presence
        sendHelloMessage(AddressType.LINK_MULTICAST.toString());

        // Listens for incoming messages
        MobileNodeListeningDaemon mobileNodeListeningDaemon = new MobileNodeListeningDaemon(this);
        // Periodically query peers if they're alive
        MobileNodeKeepaliveDaemon mobileNodeKeepaliveDaemon = new MobileNodeKeepaliveDaemon(this);

        Thread listeningDaemonThread = new Thread(mobileNodeListeningDaemon);
        Thread queryingDaemonThread = new Thread(mobileNodeKeepaliveDaemon);

        listeningDaemonThread.start();
        queryingDaemonThread.start();

        mainUserInteraction();

        mobileNodeKeepaliveDaemon.finish();
        mobileNodeListeningDaemon.finish();

        LOGGER.debug("Finishing mobile node process");
    }

    void mainUserInteraction() {
        Menu menu = new MainView().getMenu();
        String opcao;
        do {
            menu.show();
            opcao = new Scanner(System.in).nextLine().toUpperCase();
            switch(opcao) {
                case "D" : downloadInteraction();
                    break;
                case "E": break;
                default: break;
            }
        }
        while(!opcao.equals("E"));
    }

    void downloadInteraction() {
        // TODO

        System.out.println("Download interaction here");
    }

    Logger getLogger() { return LOGGER; }

    String getMacAddr() {
        return macAddr;
    }

    ContentRoutingTable getContentRoutingTable() {
        return contentRoutingTable;
    }

    PeerKeepaliveTable getPeerKeepaliveTable() {
        return peerKeepaliveTable;
    }

    public void sendHelloMessage(String dstMac) {
        MobileNetworkPDU helloPacket = new HelloMobileNetworkPDU(
                macAddr,
                dstMac,
                Business.PDU.MobileNetworkPDU.MobileNetworkMessageType.HELLO,
                Business.PDU.MobileNetworkPDU.MobileNetworkErrorType.VALID,
                62,
                String.valueOf(currentHelloSessionID),
                contentRoutingTable);
        sendPDU(helloPacket);
        currentHelloSessionID++;
    }

    public void sendPingMessage(String dstMac, String sessionID) {
        MobileNetworkPDU pingPacket = new MobileNetworkPDU(
                macAddr,
                dstMac,
                Business.PDU.MobileNetworkPDU.MobileNetworkMessageType.PING,
                Business.PDU.MobileNetworkPDU.MobileNetworkErrorType.VALID,
                62,
                sessionID);
        sendPDU(pingPacket);
    }

    public void sendPongMessage(String dstMac, String sessionID) {
        MobileNetworkPDU pongPacket = new MobileNetworkPDU(
                macAddr,
                dstMac,
                Business.PDU.MobileNetworkPDU.MobileNetworkMessageType.PONG,
                Business.PDU.MobileNetworkPDU.MobileNetworkErrorType.VALID,
                62,
                sessionID);
        sendPDU(pongPacket);
    }

    void sendPDU(MobileNetworkPDU pdu) {
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
}
