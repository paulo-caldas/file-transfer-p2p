package Business.MobileNetworkNode;

import java.io.*;
import java.net.*;
import java.util.*;

import Business.MobileNetworkNode.Daemon.MobileNodeKeepaliveDaemon;
import Business.MobileNetworkNode.Daemon.MobileNodeListeningDaemon;
import Business.MobileNetworkNode.DataCache.FileFragment;
import Business.MobileNetworkNode.DataCache.FileFragmentTable;
import Business.MobileNetworkNode.RoutingInfo.PeerKeepaliveTable;
import Business.MobileNetworkNode.RoutingInfo.RoutingTable;
import Business.MobileNetworkNode.RoutingInfo.RoutingTableEntry;
import Business.PDU.*;

import Business.Utils;
import View.DynamicContentSearchResultsView;
import View.StaticMainView;
import View.Utilities.Menu;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import static Business.MobileNetworkNode.MobileNode.AddressType.LINK_BROADCAST;
import static Business.PDU.MobileNetworkPDU.ContentType.*;
import static Business.PDU.MobileNetworkPDU.MobileNetworkErrorType.UNROUTABLE;
import static Business.PDU.MobileNetworkPDU.MobileNetworkErrorType.VALID;
import static Business.PDU.MobileNetworkPDU.STANDARD_TTL;
import static Business.Utils.getTimestampOfNow;

/**
 * A member of a mobile P2P network
 * Has methods to do certain functionalities (such as sending or receiving messages)
 * Has autonomousStart method that runs by itself (with help of daemons) and presents a textual interface
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

    // Sockets to receive and send messages
    private MulticastSocket receiveServerSocket;
    private MulticastSocket sendServerSocket;

    // Connection-related variables
    private String macAddr;
    private InetAddress group;
    private Integer port;

    // If dividing a file into UDP-sendable chunks, this is the size of each chunk
    private final int FILE_CHUNK_SIZE_BYTES = 3000;

    // Logger to log information into a file
    private final Logger LOGGER = Logger.getLogger(MobileNode.class);

    // For reading user input
    private Scanner scanner;

    /**
     * Data structures
     */

    // Maps a file's id (hash) to a list of lines on this table. Each line is a possible route to retrieve that content
    private RoutingTable contentRoutingTable;

    // Structure that allows to keep track on what peers are responding to keepalive messages.
    // If one doesn't reply in time too many times in a row, he's removed and
    private PeerKeepaliveTable<String, String> peerKeepaliveTable;

    // Maping a peer's ID to the most recent cached version of their routing table
    // Useful to quickly realize if I'm already updated on a user
    private Map<String, String> hashOfPeersMostRecentContentTable;

    // Map a file hash to a list of gathered fragments
    private FileFragmentTable<String> cacheOfFragmentedFiles;

    public MobileNode(File sharingDirectory) throws IOException{
        if (! (sharingDirectory.exists() && sharingDirectory.isDirectory())) {
            throw new IOException("No such directory");
        }

        try {
            // Setting up needed instance variables
            NetworkInterface eth0 = NetworkInterface.getByName("eth0");
            //NetworkInterface eth0 = NetworkInterface.getNetworkInterfaces().nextElement();
            group = InetAddress.getByName(AddressType.NETWORK_MULTICAST.toString());
            port = Integer.parseInt(AddressType.LISTENING_PORT.toString());
            macAddr = Utils.macByteArrToString(eth0.getHardwareAddress());

            // Setting up logger by importing log4j.xml config file
            System.setProperty("logfile.name", macAddr + "_MobileNodeLog.xml");
            DOMConfigurator.configure("log4j.xml");

            LOGGER.info("Sharing directory: " + sharingDirectory.getCanonicalPath());

            // Populating content sharing table with all files inside the sharing directory folder passed by argument
            contentRoutingTable = new RoutingTable(this.macAddr);

            // Saving files in a cache that splits them into pieces
            cacheOfFragmentedFiles = new FileFragmentTable<>();

            List<File> filesInPath = Utils.getFilesInsidePath(sharingDirectory);
            filesInPath.forEach(( file ->  {
                LOGGER.info("Indexing file: " + file.getName());
                try {

                    String key = Utils.hashFile(file, "md5");

                    synchronized (contentRoutingTable) {
                        // Add to my routing table the entry that i know, 0 hops away, my own file
                        contentRoutingTable.addOwnedReference(key, file);
                    }

                    synchronized (cacheOfFragmentedFiles) {
                        // Add to cache the fragmented pieces of my local file
                        cacheOfFragmentedFiles.insertFile(key, file, FILE_CHUNK_SIZE_BYTES);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            } ));

            // Setting up the table to help keep count of what peers did not prove they're alive
            // Max number of times they can fail to prove they're alive in a row before being removed: 3
            peerKeepaliveTable = new PeerKeepaliveTable<>("n/a", 3);

            // Setting up to table to help keep track of the hash of the last content table that we used (so we dont keep updating information we already know of)
            hashOfPeersMostRecentContentTable = new HashMap<>();

            // Setting up sockets, needed for UDP communication
            receiveServerSocket = new MulticastSocket(port);
            // Listening socket joins the multicast group associated with the mobile network
            receiveServerSocket.joinGroup(new InetSocketAddress(group, port), eth0);
            sendServerSocket = new MulticastSocket(port);

            scanner = new Scanner(System.in);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Logger getLogger() {
        return LOGGER;
    }

    public String getMacAddr() {
        return macAddr;
    }

    public RoutingTable getRoutingTable() {
        return contentRoutingTable;
    }

    public PeerKeepaliveTable<String, String> getPeerKeepaliveTable() {
        return peerKeepaliveTable;
    }

    public Map<String, String> getHashOfPeersMostRecentContentTable() {
        return hashOfPeersMostRecentContentTable;
    }

    public FileFragmentTable<String> getCachefFragmentedFiles() {
        return cacheOfFragmentedFiles;
    }

    /**
     * Execute the node's behaviour and present the user with a textual interface.
     * The behaviour stops when the user signals to quit
     * @throws InterruptedException When one or more of the daemon threads get interrupted
     */
    public void autonomousStart() throws InterruptedException {
        LOGGER.debug("Starting mobile node process");

        // Initialize into the network by announcing everyone your presence
        sendHelloMessage(AddressType.LINK_MULTICAST.toString(), getTimestampOfNow());

        // Listens for incoming messages
        MobileNodeListeningDaemon mobileNodeListeningDaemon = new MobileNodeListeningDaemon(this);
        // Periodically query peers if they're alive
        MobileNodeKeepaliveDaemon mobileNodeKeepaliveDaemon = new MobileNodeKeepaliveDaemon(this);

        // Start the threads
        Thread mobileNodeListeningDaemonThread = new Thread(mobileNodeListeningDaemon);
        Thread mobileNodeKeepAliveDaemonThread = new Thread(mobileNodeKeepaliveDaemon);

        mobileNodeListeningDaemonThread.start();
        mobileNodeKeepAliveDaemonThread.start();

        // Start the user interaction flow, where menus are shown and input is read
        mainUserInteraction();

        // If the interaction flow ended, the entire program can end
        // Signal the daemons to gracefully end and clean up
        mobileNodeKeepaliveDaemon.finish();
        mobileNodeListeningDaemon.finish();

        // Wait for threads to be finished
        mobileNodeListeningDaemonThread.join();
        mobileNodeKeepAliveDaemonThread.join();

        LOGGER.debug("Finished mobile node process");
        LOGGER.debug(getStatistics());

        // Inform user on where the log files can be reached
        System.out.println("Info logged in " + macAddr + "_MobileNodeLog.xml");
    }

    private void mainUserInteraction() {
        Menu menu = new StaticMainView().getMenu();
        String option;
        do {
            menu.show();
            option = scanner.nextLine().toUpperCase();
            switch(option) {
                case "D" : downloadInteraction();
                    break;
                case "E": System.out.println("Leaving...");
                    break;
                default: break;
            }
        } while(!option.equals("E"));
    }

    private void downloadInteraction() {
        // First ask for user to input a query string (much like a search engine)
        System.out.print("Type names to query for(Enter for all):");
        String queryString = scanner.nextLine();

        // search in local routing table for files with similar name
        Map<String,Set<RoutingTableEntry>> matchingEntriesMap;
        synchronized (contentRoutingTable) {
            matchingEntriesMap = contentRoutingTable.similarFileNameSearch(queryString);
        }

        // Auxiliary structure that maps a name to the corresponding filehash
        Map<String, String> fileHashMap = new HashMap<>();

        // Parse into a single list, where each entry is a line in user menu
        List<RoutingTableEntry> matchingEntriesInList = new ArrayList<>();

        // Populate the previous two auxiliary structures
        for (Map.Entry<String, Set<RoutingTableEntry>> entry : matchingEntriesMap.entrySet()) {
            String fileHash = entry.getKey();
            Set<RoutingTableEntry> knownPathsForHash = entry.getValue();
            knownPathsForHash.forEach(path -> fileHashMap.put(path.getFileName(), fileHash));
            matchingEntriesInList.addAll(knownPathsForHash);
        }

        // show user the results and ask him to search for one
        DynamicContentSearchResultsView contentSearchResultsView = new DynamicContentSearchResultsView();

        // Interface gives visibility priority to content less hops away
        matchingEntriesInList.sort(RoutingTableEntry::compareTo);

        matchingEntriesInList.stream().forEach(contentSearchResultsView::addOption);

        Menu menu = contentSearchResultsView.getMenu();
        boolean fileItemChosen = false;
        String option;
        int optionInt;
        do {
            menu.show();
            option = scanner.nextLine().toUpperCase();

            // Check if a number was added as input
            if ((option.matches("(0|\\d+)")) && ((optionInt = Integer.parseInt(option)) < matchingEntriesInList.size())) {

                String requestedFileName = matchingEntriesInList.get(optionInt).getFileName();
                String requestedFileHash = fileHashMap.get(requestedFileName);

                requestContentFromSingleNode(requestedFileHash);

                fileItemChosen = true;
            }

        } while(!option.equals("E") && !fileItemChosen);
    }

    private void requestContentFromSingleNode(String requestedFileHash) {
        RoutingTableEntry path;

        synchronized(contentRoutingTable) {
            try {
                path = contentRoutingTable.getBestHopCountEntry(requestedFileHash);
            } catch ( NullPointerException e) {
                LOGGER.error("Couldn't find path for requested file");
                return;
            }
        }

        if (path.getHopCount() == 0) {
            // Don't download a file we have stored locally...

            return;
        }

        String destination = path.getDstMAC();
        String nextHop = path.getNextHopMAC();
        int hopCount = path.getHopCount();
        sendRequestContentMessage(destination,nextHop,hopCount, getTimestampOfNow(), requestedFileHash);
    }

    public MobileNetworkPDU capturePDU() throws IOException, ClassNotFoundException {
        DatagramPacket receivePacket = new DatagramPacket(new byte[10000], 10000);
        receiveServerSocket.receive(receivePacket);
        byte[] data = receivePacket.getData();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream objectInputStream = new ObjectInputStream(in);
        MobileNetworkPDU pdu = (MobileNetworkPDU) objectInputStream.readObject();

        return pdu;
    }

    public void sendHelloMessage(String dstMac, String timestamp) {
        // hellos only happen on a single-hop basis
        Stack<String> nodePath = new Stack<>();
        nodePath.push(macAddr);
        nodePath.push(LINK_BROADCAST.toString());

        MobileNetworkPDU helloPacket = new DataResponseMobileNetworkPDU(
                macAddr,
                dstMac,
                VALID,
                STANDARD_TTL,
                timestamp,
                nodePath,
                ROUTING_TABLE,
                new String[0], // No params used in hello messages
                contentRoutingTable);

        LOGGER.debug("Sending(HELLO): " + helloPacket.toString());

        sendPDU(helloPacket);
    }

    public void sendErrorMessage(DataRequestMobileNetworkPDU requestPDU, MobileNetworkPDU.MobileNetworkErrorType errorToSend) {
        // The error is to be send to the one who requested
        String messageDestination = requestPDU.getSrcMAC();

        // sessionID stays the same to we know what the error refers to
        String sessionID = requestPDU.getSessionID();

        // So say were node C and we receive
        // B -> [A,B,(C)] ---> C
        // B -> [A,(B)]   <--- C
        Stack<String> nodePath = requestPDU.getNodePath();

        nodePath.pop();

        MobileNetworkPDU errorPacket = new DataResponseMobileNetworkPDU(
                macAddr,
                messageDestination,
                errorToSend,
                STANDARD_TTL,
                sessionID,
                nodePath,
                ERROR,
                new String[0], // No params user in error messages
                null); // Error messages return nothing

        LOGGER.error("SENDING(ERROR): " + errorPacket.toString());

        sendPDU(errorPacket);
    }

    public void sendPingMessage(String dstMac, String timestamp) {
        // pongs only happen on a single-hop basis
        Stack<String> nodePath = new Stack<>();
        nodePath.push(macAddr);
        nodePath.push(LINK_BROADCAST.toString());

        MobileNetworkPDU pingPacket = new DataRequestMobileNetworkPDU(
                macAddr,
                dstMac,
                VALID,
                STANDARD_TTL,
                timestamp,
                nodePath,
                ROUTING_TABLE_VERSION,
                new String[0]);

        LOGGER.debug("Sending(PING): " + pingPacket.toString());

        sendPDU(pingPacket);
    }

    public void sendPongMessage(String dstMac, String timestamp, String routingTableVersion) {
        // pongs only happen on a single-hop basis
        Stack<String> nodePath = new Stack<>();
        nodePath.push(macAddr);
        nodePath.push(LINK_BROADCAST.toString());

        MobileNetworkPDU pongPacket = new DataResponseMobileNetworkPDU(
                macAddr,
                dstMac,
                VALID,
                STANDARD_TTL,
                timestamp,
                nodePath,
                ROUTING_TABLE_VERSION,
                new String[0], // No params in pong message
                routingTableVersion
        );

        LOGGER.debug("Sending(PONG): " + pongPacket.toString());

        sendPDU(pongPacket);
    }

    public void sendRemoveUpdate(RoutingTable removedTable, String timestamp) {
        // send remove updates only happen on a single-hop basis
        Stack<String> nodePath = new Stack<>();
        nodePath.push(macAddr);
        nodePath.push(LINK_BROADCAST.toString());

        MobileNetworkPDU removeUpdatePacket = new DataResponseMobileNetworkPDU(
                macAddr,
                LINK_BROADCAST.toString(),
                VALID,
                STANDARD_TTL,
                timestamp,
                nodePath,
                ROUTING_TABLE_DELETION_UPDATE,
                new String[0], // No params in updates
                removedTable
        );
        LOGGER.debug("Sending(UPDT_REM): " + removeUpdatePacket.toString());

        sendPDU(removeUpdatePacket);
    }

    public void sendInsertUpdate(RoutingTable insertedTable, String timestamp) {
        // send insert updates only happen on a single-hop basis
        Stack<String> nodePath = new Stack<>();
        nodePath.push(macAddr);
        nodePath.push(LINK_BROADCAST.toString());

        MobileNetworkPDU removeUpdatePacket = new DataResponseMobileNetworkPDU(
                macAddr,
                LINK_BROADCAST.toString(),
                VALID,
                STANDARD_TTL,
                timestamp,
                nodePath,
                ROUTING_TABLE_INSERTION_UPDATE, // No params in updates
                new String[0],
                insertedTable
        );
        LOGGER.debug("Sending(UPDT_INSRT): " + removeUpdatePacket.toString());

        sendPDU(removeUpdatePacket);
    }

    public void sendRequestContentMessage(String dstMac, String nextHopMAC, int maxHopCount, String timestamp, String fileHash) {
        Stack<String> nodePath = new Stack<>();
        nodePath.push(macAddr);
        nodePath.push(nextHopMAC);

        String[] params = new String[1];
        params[0] = fileHash;

        MobileNetworkPDU requestContentPacket = new DataRequestMobileNetworkPDU(
                macAddr,
                dstMac,
                VALID,
                STANDARD_TTL,
                timestamp,
                nodePath,
                FILE,
                params);

        LOGGER.debug("Sending(REQ_FILE): " + requestContentPacket.toString());

        sendPDU(requestContentPacket);
    }

    public void sendResponseFileMessage(DataRequestMobileNetworkPDU requestItRespondsTo, FileFragment fragment, String[] params) {
        String requestSessionID = requestItRespondsTo.getSessionID();
        Stack<String> nodePath = requestItRespondsTo.getNodePath();
        String peerIDOfRequester = nodePath.get(0);

        // A node path was made whilst the request message was routed
        // To reply, simply pop the last element (which should be our node), and forward the message back
        nodePath.pop();

        MobileNetworkPDU responseFileMessage = new DataResponseMobileNetworkPDU(
                macAddr,
                peerIDOfRequester,
                VALID,
                STANDARD_TTL,
                requestSessionID,
                nodePath,
                FILE,
                params,
                fragment);

        LOGGER.debug("Sending(RESPONSE_FILE): " + responseFileMessage.toString());

        sendPDU(responseFileMessage);
    }

    public void sendHelloRequest(String dstMac, String timestamp) {
        Stack<String> nodePath = new Stack<>();
        nodePath.push(macAddr);
        nodePath.push(dstMac);

        MobileNetworkPDU requestContentPacket = new DataRequestMobileNetworkPDU(
                macAddr,
                dstMac,
                VALID,
                STANDARD_TTL,
                timestamp,
                nodePath,
                ROUTING_TABLE,
                new String[0]); // Hell request has no params

        LOGGER.debug("Sending(REQ_HELLO): " + requestContentPacket.toString());

        sendPDU(requestContentPacket);
    }


    public void forwardResponseContentPacket(DataResponseMobileNetworkPDU responsePDU) {
        Stack<String> nodePath = responsePDU.getNodePath();

        // Pop the next hop (because that's me and I'm already looking at it)
        nodePath.pop();

        // Only the next hop changed, re-send message
        this.sendPDU(responsePDU);
    }

    public void forwardRequestContentPacket(DataRequestMobileNetworkPDU requestPDU) {
        String messageDestination = requestPDU.getDstMAC();

        String nextHop;
        synchronized (contentRoutingTable) {
            nextHop = contentRoutingTable.getNextPeerHop(messageDestination);
        }

        if (nextHop != null) {
            Stack<String> nodePath = requestPDU.getNodePath();

            // Look to the first empty value in array and push the new next hop
            nodePath.push(nextHop);

            // Only the next hop changed, re-send message
            LOGGER.info("Forwarding (to " + nextHop + "):" + requestPDU.toString());

            this.sendPDU(requestPDU);
        } else {
            sendErrorMessage(requestPDU, UNROUTABLE);
        }
    }

    public void sendPDU(MobileNetworkPDU pdu) {
        // Send the pdu as a byte array
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(outputStream);

            os.writeObject(pdu);
            os.flush();

            byte[] buffer = outputStream.toByteArray();

            DatagramPacket sendPacket = new DatagramPacket(buffer, buffer.length, group, port);
            sendPacket.setData(buffer);
            sendServerSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getStatistics() {
        String nodeInfo = String.format("STATISTICS: \n-Mac address: %s \n-Routing table \n %s \n-Peer Keepalive table\n %s",
                macAddr,
                contentRoutingTable.toString(),
                peerKeepaliveTable.toString());

        return nodeInfo;
    }

    public void closeReceiveSocket() {
        this.receiveServerSocket.close();
    }
}
