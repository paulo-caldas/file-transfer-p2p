package Business;

/*
 * Single entry in the mobile routing table
 */
public class MobileRoutingTableEntry {
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
