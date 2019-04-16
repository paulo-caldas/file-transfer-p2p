package Business;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public List<B> applyStrikeWave() {
        List<B> removedPeers = new ArrayList<>();
        for (Map.Entry<B, Integer> entry : peerKeepaliveTable.entrySet()) {
            int newStrikeValue = entry.getValue() + 1;
            entry.setValue(newStrikeValue);

            if (newStrikeValue >= MAX_STRIKES) {
                B removedPeer = entry.getKey();
                peerKeepaliveTable.remove(removedPeer);
                removedPeers.add(removedPeer);
            }
        }

        return applyStrikeWave();
    }

    public void markAsAlive(B peerId) {
        System.out.println(" Marking as alive " + peerId);
        peerKeepaliveTable.put(peerId, -1);
    }

    public void markAsAlive(A receivedSessionId,B peerId) {
        if (receivedSessionId.equals(currentKeepaliveSessionID)) {
            markAsAlive(peerId);
        }
    }

}
