package Business.MobileNetworkNode.RoutingInfo;

import java.util.*;

/**
 * Class that saves B's and how many strikes they have. Additionally, there are methods to clean
 * their strikes and to apply a global strike wave, that incurs in the B's being removed if they
 * have too many strikes in a row. This is set up by there being a current ID of type A that defines
 * the current strike session, users must reply with a pong of some kind that references the most current
 * ID.
 *
 * Example usage:
 * - Send a broadcast "PING" to your peers with sessionID = X
 * - wait TIMEOUT_TIME
 * - apply strike wave, aka increment the number of strikes to everyone that didn't manage to get
 * a valid markAsAlive during that time. Remove those that failed too many times in a row
 * @param <A> Session ID (An integer version, a String timestamp, etc)
 * @param <B> Class to be monitoring (An integer or String ID, etc)
 */
public class PeerKeepaliveTable<A,B> {
    // Session ID that defines the current strike session
    private A currentKeepaliveSessionID;

    // Number of strikes a member can have until it is removed
    private final int MAX_STRIKES;

    // Map a member to the number of strikes he has
    private Map<B, Integer> peerKeepaliveTable;

    /**
     * Constructor
     * @param startSessionID The session ID to start with
     * @param maxStrikes Max number of strikes allowed of a member
     */
    public PeerKeepaliveTable(A startSessionID, int maxStrikes) {
        this.currentKeepaliveSessionID = startSessionID;
        this.MAX_STRIKES = maxStrikes > 0 ? maxStrikes : 1;
        this.peerKeepaliveTable = new HashMap<>();
    }

    public Set<B> getPeers() {
        return peerKeepaliveTable.keySet();
    }

    public boolean hasPeer(B peerId) {
        return peerKeepaliveTable.containsKey(peerId);
    }

    public void setCurrentKeepaliveSessionID(A currentKeepaliveSessionID) {
        this.currentKeepaliveSessionID = currentKeepaliveSessionID;
    }

    public void markAsAlive(B peerId) {
        peerKeepaliveTable.put(peerId, -1);
    }

    public boolean markAsAlive(A receivedSessionId,B peerId) {
        if (receivedSessionId.equals(currentKeepaliveSessionID)) {
            markAsAlive(peerId);
            return true;
        }

        return false;
    }

    public List<B> applyStrikeWave() {
        List<B> removedPeers = new ArrayList<>();
        for (Map.Entry<B, Integer> entry : peerKeepaliveTable.entrySet()) {
            int newStrikeValue = entry.getValue() + 1;
            entry.setValue(newStrikeValue);

            if (newStrikeValue >= MAX_STRIKES) {
                B removedPeer = entry.getKey();
                removedPeers.add(removedPeer);
            }
        }

        // We only remove at the end, collectively, for it to be thread safe
        peerKeepaliveTable.keySet().removeAll(removedPeers);

        return removedPeers;
    }

    public void removePeer(A peerID) {
       peerKeepaliveTable.remove(peerID);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("max_strikes: " + MAX_STRIKES + "\n");
        sb.append("current_session_id: " + currentKeepaliveSessionID + "\n\n");
        peerKeepaliveTable.entrySet().forEach(
                set -> sb.append(set.getKey() + " -> " + set.getValue() + "\n")
        );
        sb.append("\n");
        sb.append("}");
        return peerKeepaliveTable.toString();
    }
}
