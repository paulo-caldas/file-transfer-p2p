package Business;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContentRoutingTable implements Map<String, ContentRoutingTableEntry>, Serializable {

    private long currentTableVersion;
    private String ownerID;
    private Map<String, ContentRoutingTableEntry> contentRoutingTable;

    public ContentRoutingTable(String ownerID) {
        this.currentTableVersion = System.currentTimeMillis();
        this.ownerID = ownerID;
        this.contentRoutingTable = new HashMap<>();
    }

    public void addOwnedReference(File file) throws IOException, NoSuchAlgorithmException {
        String fileHash = Utils.hashFile(file, "md5");
        contentRoutingTable.put(fileHash, new ContentRoutingTableEntry(fileHash, ownerID, "self", 0, currentTableVersion));
    }

    public void addReference(String fileHash, String destination, String nextHop, Integer hopCount) {
        contentRoutingTable.put(
                fileHash,
                new ContentRoutingTableEntry(
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
    public ContentRoutingTableEntry get(Object o) {
        return contentRoutingTable.get(o);
    }

    @Override
    public ContentRoutingTableEntry put(String s, ContentRoutingTableEntry mobileRoutingTableEntry) {
        return contentRoutingTable.put(s, mobileRoutingTableEntry);
    }

    @Override
    public ContentRoutingTableEntry remove(Object o) {
        return contentRoutingTable.remove(o);
    }

    @Override
    public void putAll(Map<? extends String, ? extends ContentRoutingTableEntry> map) {
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
    public Collection<ContentRoutingTableEntry> values() {
        return contentRoutingTable.values();
    }

    @Override
    public Set<Entry<String, ContentRoutingTableEntry>> entrySet() {
        return contentRoutingTable.entrySet();
    }

    public Long getMostRecentEntryVersionOfPeer(String peerID) {
        return contentRoutingTable.values()
                                  .stream() .filter(
                                          v -> v.getNextHopMAC().equals(peerID))
                                  .map(ContentRoutingTableEntry::getVersion)
                                  .max(Long::compareTo)
                                  .orElse(-1L);
    }
}

class ContentRoutingTableEntry implements Serializable {
    private String fileHash;
    private String dstMAC;
    private String nextHopMAC;
    private int hopCount;
    private long versionOfEntry;

    public ContentRoutingTableEntry(String fileHash, String dstMAC, String nextHopMAC, int hopCount, long versionOfEntry) {
        this.fileHash = fileHash;
        this.dstMAC = dstMAC;
        this.nextHopMAC = nextHopMAC;
        this.hopCount = hopCount;
        this.versionOfEntry = versionOfEntry;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getDstMAC() {
        return dstMAC;
    }

    public void setDstMAC(String dstMAC) {
        this.dstMAC = dstMAC;
    }

    public String getNextHopMAC() {
        return nextHopMAC;
    }

    public void setNextHopMAC(String nextHopMAC) {
        this.nextHopMAC = nextHopMAC;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }

    public long getVersion() {
        return this.versionOfEntry;
    }

    public void setVersion(long version) {
        this.versionOfEntry = version;
    }
}
