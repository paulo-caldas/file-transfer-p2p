package Business.MobileNetworkNode;

import java.util.*;

public class PeerKeepaliveTable<A,B> {
    private A currentKeepaliveSessionID;
    private final int MAX_STRIKES;
    private Map<B, Integer> peerKeepaliveTable;

    public PeerKeepaliveTable(A startSessionID, int maxStrikes) {
        this.currentKeepaliveSessionID = startSessionID;
        this.MAX_STRIKES = maxStrikes > 0 ? maxStrikes : 1;
        this.peerKeepaliveTable = new HashMap<>();
    }

    public void setCurrentKeepaliveSessionID(A currentKeepaliveSessionID) {
        this.currentKeepaliveSessionID = currentKeepaliveSessionID;
    }

    public boolean hasPeer(B peerId) {
        return peerKeepaliveTable.containsKey(peerId);
    }

    public Set<B> getPeers() {
        return peerKeepaliveTable.keySet();
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

        peerKeepaliveTable.keySet().removeAll(removedPeers);
        return removedPeers;
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
