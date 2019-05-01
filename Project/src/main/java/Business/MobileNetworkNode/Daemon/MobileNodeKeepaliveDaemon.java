package Business.MobileNetworkNode.Daemon;

import Business.MobileNetworkNode.PeerKeepaliveTable;
import Business.MobileNetworkNode.MobileNode;
import Business.MobileNetworkNode.RoutingInfo.RoutingTable;
import org.apache.log4j.Logger;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Daemon responsible for, in behalf of a mobile node, testing for the presence
 * of his peers and, if they are unable to prove so, remove them
 */
public class MobileNodeKeepaliveDaemon implements MobileNodeDaemon {
    private boolean finished;
    private final MobileNode representativeNode;
    private final PeerKeepaliveTable keepaliveTable;
    private final RoutingTable contentRoutingTable;
    private Map<String, Integer> hashOfPeersMostRecentContentTable;
    private final int KEEPAWAY_TIME_MS = 5000;
    private final int HELLO_FLOODING_PERIODICITY = 3; // every 3 KEEPARAY_TIME_MS, flood with HELLO
    private final Logger LOGGER;

    public MobileNodeKeepaliveDaemon(MobileNode representativeNode) {
        this.finished = false;
        this.representativeNode = representativeNode;
        this.keepaliveTable = representativeNode.getPeerKeepaliveTable();
        this.contentRoutingTable = representativeNode.getRoutingTable();
        this.hashOfPeersMostRecentContentTable = representativeNode.getHashOfPeersMostRecentContentTable();
        this.LOGGER = representativeNode.getLogger();
    }

    @Override
    public void run () {
        System.out.println("- Starting keepalive daemon");
        queryPeers();
    }

    @Override
    public void finish() {
        finished = true;
    }

    private void queryPeers() {

        int period = 0;

        try {
            while (!finished) {
                pingAllPeers();
                Thread.sleep(KEEPAWAY_TIME_MS);
                removeDeadPeers();

                if (period++ == HELLO_FLOODING_PERIODICITY) {
                    // TODO: ping flood?
                    sendHelloFlood();
                    period = 0;
                }

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void pingAllPeers() {
        String timestampOfNow = new Timestamp(Calendar.getInstance().getTime().getTime()).toString();
        synchronized (keepaliveTable) {
            keepaliveTable.setCurrentKeepaliveSessionID(timestampOfNow);
            keepaliveTable.getPeers().forEach(peer -> representativeNode.sendPingMessage((String) peer, timestampOfNow) );
        }
    }

    private void removeDeadPeers() {
        List<String> removedPeers;

        // Temove idle peers from the keepalive table
        synchronized (keepaliveTable) {
            removedPeers = keepaliveTable.applyStrikeWave();
        }

        // The previous method returns all the peers that were removed
        // All that is left is to remove all references of those peers
        // From the content table
        removedPeers.forEach(peer -> {
            synchronized (contentRoutingTable) {
                contentRoutingTable.removePeer(peer);
            }
            synchronized (hashOfPeersMostRecentContentTable) {
                hashOfPeersMostRecentContentTable.remove(peer);
            }

        });

        LOGGER.info("Removed peers: " + removedPeers.toString());
    }

    private void sendHelloFlood() {
        representativeNode.sendHelloMessage(MobileNode.AddressType.LINK_MULTICAST.toString());
    }

    private void sendPingFlood() {
        representativeNode.sendPingMessage(MobileNode.AddressType.LINK_MULTICAST.toString(), "-1");
    }
}


