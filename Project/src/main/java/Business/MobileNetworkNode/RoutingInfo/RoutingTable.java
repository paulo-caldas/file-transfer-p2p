package Business.MobileNetworkNode.RoutingInfo;

import Business.Utils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RoutingTable implements Map<String, RoutingTableEntry>, Serializable {

    private long currentTableVersion;
    private String ownerID;
    private Map<String, RoutingTableEntry> contentRoutingTable;

    public RoutingTable(String ownerID) {
        this.currentTableVersion = System.currentTimeMillis();
        this.ownerID = ownerID;
        this.contentRoutingTable = new HashMap<>();
    }

    public void addOwnedReference(File file) throws IOException, NoSuchAlgorithmException {
        String fileHash = Utils.hashFile(file, "md5");
        contentRoutingTable.put(fileHash, new RoutingTableEntry(fileHash, ownerID, "self", 0, currentTableVersion));
    }

    public void addReference(String fileHash, String destination, String nextHop, Integer hopCount) {
        contentRoutingTable.put(
                fileHash,
                new RoutingTableEntry(
                        fileHash,
                        destination,
                        nextHop,
                        hopCount,
                        currentTableVersion
                ));
    }

    public void setVersion(long version) {
        this.currentTableVersion = version;
    }

    public void incVersion() {
        this.currentTableVersion++;
    }

    public long getCurrentTableVersion() {
        return currentTableVersion;
    }

    @Override
    public int size() {
        return contentRoutingTable.size();
    }

    @Override
    public boolean isEmpty() {
        return contentRoutingTable.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return contentRoutingTable.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return contentRoutingTable.containsValue(o);
    }

    @Override
    public RoutingTableEntry get(Object o) {
        return contentRoutingTable.get(o);
    }

    @Override
    public RoutingTableEntry put(String s, RoutingTableEntry mobileRoutingTableEntry) {
        return contentRoutingTable.put(s, mobileRoutingTableEntry);
    }

    @Override
    public RoutingTableEntry remove(Object o) {
        return contentRoutingTable.remove(o);
    }

    @Override
    public void putAll(Map<? extends String, ? extends RoutingTableEntry> map) {
        contentRoutingTable.putAll(map);
    }

    @Override
    public void clear() {
        contentRoutingTable.clear();
    }

    @Override
    public Set<String> keySet() {
        return contentRoutingTable.keySet();
    }

    @Override
    public Collection<RoutingTableEntry> values() {
        return contentRoutingTable.values();
    }

    @Override
    public Set<Entry<String, RoutingTableEntry>> entrySet() {
        return contentRoutingTable.entrySet();
    }

    public Long getMostRecentEntryVersionOfPeer(String peerID) {
        return contentRoutingTable.values()
                .stream() .filter(
                        v -> v.getNextHopMAC().equals(peerID))
                .map(RoutingTableEntry::getVersion)
                .max(Long::compareTo)
                .orElse(-1L);
    }
}
