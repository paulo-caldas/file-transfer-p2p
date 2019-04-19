package Business;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ContentRoutingTable implements Map<String, MobileRoutingTableEntry>, Serializable {

    private String ownerID;
    private Map<String, MobileRoutingTableEntry> contentRoutingTable;

    public ContentRoutingTable(String ownerID) {
        this.ownerID = ownerID;
        this.contentRoutingTable = new HashMap<>();
    }

    /**
     * Add everything in that folder and subfolders
     *
     * @param initialPath directory to start from
     */
    public void recursivePopulateWithLocalContent(File initialPath) throws IOException, NoSuchAlgorithmException {
        for (File currPath : initialPath.listFiles()) {
            if (currPath.isFile()) {
                String fileHash = Utils.hashFile(currPath, "md5");
                contentRoutingTable.put(fileHash, new MobileRoutingTableEntry(fileHash, ownerID, null, 0));
            } else {
                recursivePopulateWithLocalContent(currPath);
            }
        }
    }

    public void mergeWithPeerContentTable(ContentRoutingTable peerRoutingTable, String peerID) {

        for (Map.Entry<String, MobileRoutingTableEntry> tableEntry : peerRoutingTable.entrySet()) {
            this.put(
                    tableEntry.getKey(),
                    new MobileRoutingTableEntry(
                            tableEntry.getValue().getFileHash(),
                            tableEntry.getValue().getDstMAC(),
                            peerID,
                            1 + tableEntry.getValue().getHopCount()
                    )
            );
        }
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
    public MobileRoutingTableEntry get(Object o) {
        return contentRoutingTable.get(o);
    }

    @Override
    public MobileRoutingTableEntry put(String s, MobileRoutingTableEntry mobileRoutingTableEntry) {
        return contentRoutingTable.put(s, mobileRoutingTableEntry);
    }

    @Override
    public MobileRoutingTableEntry remove(Object o) {
        return contentRoutingTable.remove(o);
    }

    @Override
    public void putAll(Map<? extends String, ? extends MobileRoutingTableEntry> map) {
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
    public Collection<MobileRoutingTableEntry> values() {
        return contentRoutingTable.values();
    }

    @Override
    public Set<Entry<String, MobileRoutingTableEntry>> entrySet() {
        return contentRoutingTable.entrySet();
    }
}

class MobileRoutingTableEntry {
    private String fileHash;
    private String dstMAC;
    private String nextHopMAC;
    private int hopCount;

    public MobileRoutingTableEntry(String fileHash, String dstMAC, String nextHopMAC, int hopCount) {
        this.fileHash = fileHash;
        this.dstMAC = dstMAC;
        this.nextHopMAC = nextHopMAC;
        this.hopCount = hopCount;
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
}
