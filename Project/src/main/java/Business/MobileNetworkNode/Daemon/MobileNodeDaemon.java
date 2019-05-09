package Business.MobileNetworkNode.Daemon;

import Business.MobileNetworkNode.DataCache.FileFragment;
import Business.MobileNetworkNode.DataCache.FileFragmentTable;
import Business.MobileNetworkNode.MobileNode;
import Business.MobileNetworkNode.RoutingInfo.PeerKeepaliveTable;
import Business.MobileNetworkNode.RoutingInfo.RoutingTable;
import Business.MobileNetworkNode.RoutingInfo.RoutingTableEntry;
import Business.PDU.DataRequestMobileNetworkPDU;
import Business.PDU.DataResponseMobileNetworkPDU;
import Business.PDU.MobileNetworkPDU;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mobile Daemon classes operate in behalf of the MobileNode. Classes that extend this abstract class
 * implement, using these operations, functionalities to do on behalf ot the MobileNode.
 *
 * Basically, the daemon needs to:
 * - Have references to a MobileNode's data structures
 *
 * - Implement Runnable (thus have a way to start it via the Thread class)
 * - Implement finish(), to signal a graceful ending. So we can call thread.join() and await for all resources to terminate
 * - Implement isFinished(), to know when the thread has stopped
 *
 * Since this class, by definition, has copies to a Node's structures, all operations
 * should be thread safe in nature (At the moment, synchronized was chosen)
 */
public abstract class MobileNodeDaemon implements Runnable {
    /**
     * Classes "borrowed" from representative node. As such, handle them with synchronized for thread safety!
     */
    // Node that this daemon represents
    private final MobileNode representativeNode;

    // Use a timestamp (String) to identify the current session in place (so late "pongs" are discarded)
    // Map a peer (String) to how many times they failed to prove they're alive. 3 strikes, you're out
    private final PeerKeepaliveTable<String, String> keepaliveTable;

    // Table with information on how to route a packet to reach a file
    private final RoutingTable contentRoutingTable;

    // Map a peer to the most recent version of they're table we've cached.
    // Useful to identify if an "hello" packet can be discarded for being rundant
    private final Map<String, String> hashOfPeersMostRecentContentTable;

    // Map a file hash to a list of gathered fragments
    private final FileFragmentTable<String> cacheOfFragmentedFiles;

    // Log in behalf of the representative node
    private final Logger LOGGER;

    private final String NODE_MAC_ADDRESS;

    /**
     * Variables that only the daemon owns
     */
    // Has the thread finished its job (used for graceful termination of threads)
    private boolean isFinished;

    /**
     * Constructor
     * @param mobileNode Mobile node that this daemon will operate in behalf of
     */
    public MobileNodeDaemon(MobileNode mobileNode) {
        this.representativeNode = mobileNode;
        this.keepaliveTable = representativeNode.getPeerKeepaliveTable();
        this.contentRoutingTable = representativeNode.getRoutingTable();
        this.hashOfPeersMostRecentContentTable = representativeNode.getHashOfPeersMostRecentContentTable();
        this.cacheOfFragmentedFiles = representativeNode.getCachefFragmentedFiles();
        this.LOGGER = representativeNode.getLogger();
        this.NODE_MAC_ADDRESS = representativeNode.getMacAddr();
        this.isFinished = false;
    }

    /**
     * =================== Functionalities: Retrieving node related information
     */

    Logger getLogger() {
        return LOGGER;
    }

    String getNodeMacAdress() {
        return NODE_MAC_ADDRESS;
    }

    /**
     * =================== Functionalities: Retrieving Daemon related information
     */

    public void finish() {
        this.isFinished = true;
    }

    public boolean isFinished() {
        return this.isFinished;
    }

    /**
     * =================== Functionalities: Receiving packets
     */

    void stopListening() {
        representativeNode.closeReceiveSocket();
    }

    MobileNetworkPDU capturePDU() throws IOException, ClassNotFoundException {
        return representativeNode.capturePDU();
    }

    /**
     * =================== Functionalities: Sending packets
     */

    void sendPingMessage(String dstMac, String timestamp) {
        representativeNode.sendPingMessage(dstMac, timestamp);
    }

    void sendPongMessage(String dstMac, String timestamp, String routingTableVersion) {
        representativeNode.sendPongMessage(dstMac, timestamp, routingTableVersion);
    }

    void sendHelloMessage(String dstMac, String timestamp) {
        representativeNode.sendHelloMessage(dstMac, timestamp);
    }

    void sendPacket(MobileNetworkPDU pdu) {
        representativeNode.sendPDU(pdu);
    }

    void sendInsertUpdate(RoutingTable insertedTable, String timestamp) {
        representativeNode.sendInsertUpdate(insertedTable, timestamp);
    }

    void sendRemoveUpdate(RoutingTable removedTable, String timestamp) {
        representativeNode.sendRemoveUpdate(removedTable, timestamp);
    }

    void sendHelloRequest(String peerID, String timestamp) {
        representativeNode.sendHelloRequest(peerID, timestamp);
    }

    void sendResponseFileMessage(DataRequestMobileNetworkPDU requestItRespondsTo, FileFragment fragment, String[] params) {
        representativeNode.sendResponseFileMessage(requestItRespondsTo, fragment, params);
    }

    void forwardResponsePacket(DataResponseMobileNetworkPDU responsePDU) {
        representativeNode.forwardResponseContentPacket(responsePDU);
    }

    void forwardRequestPacket(DataRequestMobileNetworkPDU requestPDU) {
        representativeNode.forwardRequestContentPacket(requestPDU);
    }

    /**
     * =================== Functionalities: Operate on the KeepaliveTable
     */

    boolean isPeerUnderMonitoring(String peerID) {
        synchronized (keepaliveTable) {
            return keepaliveTable.hasPeer(peerID);
        }
    }

    void setCurrentKeepaliveSessionId(String sessionID) {
        synchronized (keepaliveTable) {
            keepaliveTable.setCurrentKeepaliveSessionID(sessionID);
        }
    }

    void markPeerAsAlive(String peerID) {
        synchronized (keepaliveTable) {
            keepaliveTable.markAsAlive(peerID);
        }
    }

    boolean markPeerAsAlive(String timestamp, String peerID) {
        synchronized (keepaliveTable) {
            return keepaliveTable.markAsAlive(timestamp, peerID);
        }
    }

    List<String> applyStrikeWaveOnMonitorizedPeers() {
        synchronized (keepaliveTable) {
            return keepaliveTable.applyStrikeWave();
        }
    }

    void cacheMostRecentPeerReference(String peerID, String hashOfReceivedTable) {
        synchronized (hashOfPeersMostRecentContentTable) {
            hashOfPeersMostRecentContentTable.put(peerID, hashOfReceivedTable);
        }
    }

    String getMostRecentPeerReferenceOrDefault(String peerID, String def) {
        synchronized (hashOfPeersMostRecentContentTable) {
            return hashOfPeersMostRecentContentTable.getOrDefault(peerID, def);
        }

    }

    /**
     * =================== Functionalities: Operate on the Routing Information
     */

    Map<String, Set<RoutingTableEntry>> removeAllReferencesOfPeer(String peerID) {
        synchronized (hashOfPeersMostRecentContentTable) {
            hashOfPeersMostRecentContentTable.remove(peerID);
        }

        synchronized (keepaliveTable) {
            keepaliveTable.removePeer(peerID);
        }

        synchronized (contentRoutingTable) {
            return contentRoutingTable.removeEntriesWithNextHop(peerID);
        }
    }

    String getTableVersion() {
        synchronized (contentRoutingTable) {
            return contentRoutingTable.getVersion();
        }
    }

    String getNextPeerHop(String destination) throws NullPointerException {
        synchronized (contentRoutingTable) {
            return contentRoutingTable.getNextPeerHop(destination);
        }
    }

    boolean deleteEntries(RoutingTable content, String peerID) {
        synchronized (contentRoutingTable) {
            return contentRoutingTable.removeEntries(content, peerID);
        }

    }

    boolean addEntries(RoutingTable pathsToAdd) {
        synchronized (contentRoutingTable) {
            return contentRoutingTable.addEntries(pathsToAdd);
        }
    }

    /**
     * =================== Functionalities: Operating on the local storage of file fragments
     */

    boolean addFragmentToCache(String key, FileFragment fragmentToAdd) throws IOException {
        synchronized (cacheOfFragmentedFiles) {
            return cacheOfFragmentedFiles.putFragment(key, fragmentToAdd);
        }
    }

    FileFragment getFragment(String key, int requestedInitByte) {
        synchronized (cacheOfFragmentedFiles) {
            return cacheOfFragmentedFiles.getFragment(key, requestedInitByte);
        }
    }
}
