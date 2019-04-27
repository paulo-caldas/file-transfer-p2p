package Business.MobileNetworkNode.RoutingInfo;

import java.io.Serializable;

public class RoutingTableEntry implements Serializable {
    private String fileHash;
    private String dstMAC;
    private String nextHopMAC;
    private int hopCount;
    private long versionOfEntry;

    public RoutingTableEntry(String fileHash, String dstMAC, String nextHopMAC, int hopCount, long versionOfEntry) {
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

