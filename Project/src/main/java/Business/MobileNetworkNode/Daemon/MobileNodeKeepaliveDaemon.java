package Business.MobileNetworkNode.Daemon;

import Business.MobileNetworkNode.MobileNode;
import Business.MobileNetworkNode.RoutingInfo.RoutingTable;
import Business.MobileNetworkNode.RoutingInfo.RoutingTableEntry;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static Business.MobileNetworkNode.MobileNode.AddressType.LINK_BROADCAST;
import static Business.Utils.getTimestampOfNow;

/**
 * Daemon responsible for, in behalf of a mobile node, test for the presence
 * of his peers and, if they are unable to prove so, remove them
 */
public class MobileNodeKeepaliveDaemon extends MobileNodeDaemon {
    // How many time does the daemon wait for peers to prove they're alive
    // Slower the number, more demanding the daemon is for a high quality link (low RTT)
    private final int KEEPAWAY_TIME_MS = 5000;

    // Log in behalf of the representative node
    private final Logger LOGGER;

    /**
     * Constructor
     * @param representativeNode Node that this daemon works in behalf of
     */
    public MobileNodeKeepaliveDaemon(MobileNode representativeNode) {
        super(representativeNode); // Init all services
        this.LOGGER = super.getLogger();
    }

    @Override
    public void run() {
        LOGGER.debug("Starting keepalive daemon");
        monitorPeers();
        LOGGER.debug("Terminating keepalive daemon");
    }

    private void monitorPeers() {
        try {
            while (!super.isFinished()) {
                pingAllPeers();
                Thread.sleep(KEEPAWAY_TIME_MS); // Give peers time to pong me as proof they're alive
                removeDeadPeers();
            }
        } catch (InterruptedException e) {
            LOGGER.error("Prematurely terminated");
        }
    }

    private void pingAllPeers() {
        // Setting the ID as a timestamp of now prevents outdated responses being accepted
        String timestampOfNow = getTimestampOfNow();
        super.setCurrentKeepaliveSessionId(timestampOfNow);

            // Send PING message in broadcast
            // The peers I know will pong back, the ones I don't know introduce themselves
            super.sendPingMessage(LINK_BROADCAST.toString(), timestampOfNow);
    }

    private void removeDeadPeers() {
        List<String> removedPeers;

        // Method applyStrikeWave sees who failed to prove they're alive,
        // and removes those that failed too many times in a row
        removedPeers = super.applyStrikeWaveOnMonitorizedPeers();

        boolean wereAnyPeersRemoved = removedPeers.size() > 0;

        String versionBeforeRemoval = super.getTableVersion();

        if (wereAnyPeersRemoved) {
            Map<String, Set<RoutingTableEntry>> pathsRemoved = new HashMap<>();

            // The previous method returns all the peers that were removed
            // All that is left is to remove all references of those peers
            // From the content table
            for (String removedPeer : removedPeers) {
                Map<String, Set<RoutingTableEntry>> removedTable = super.removeAllReferencesOfPeer(removedPeer);

                for (Map.Entry<String, Set<RoutingTableEntry>> removedPaths : removedTable.entrySet()) {
                    String fileHash = removedPaths.getKey();

                    if (!pathsRemoved.containsKey(fileHash)) {
                        pathsRemoved.put(fileHash, removedPaths.getValue());
                    } else {
                        Set<RoutingTableEntry> pathsToRemoveAlreadyKnown = pathsRemoved.get(fileHash);
                        pathsToRemoveAlreadyKnown.addAll(removedPaths.getValue());
                        pathsRemoved.put(fileHash, pathsToRemoveAlreadyKnown);
                    }
                }

                RoutingTable singleRemoveInstruction = new RoutingTable(removedPeer, removedTable);
                // FIXME: incorrect values
                singleRemoveInstruction.setPreviousVersion(versionBeforeRemoval);
                singleRemoveInstruction.setVersion(super.getTableVersion());

                // New changes were made, send them out to peers (similarly to distance vector protocol RIP)
                super.sendRemoveUpdate(singleRemoveInstruction, getTimestampOfNow());
            }

            LOGGER.info("Removed peers: " + removedPeers.toString());
        }
    }
}


