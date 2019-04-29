package Business.PDU;


public class ContentMobileNetworkPDU extends MobileNetworkPDU {
    String[] nodePath;

    public ContentMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, String[] nodePath) {
            super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID);
            this.nodePath = nodePath;
    }

    public String[] getNodePath() { return nodePath; }
    public void setNodePath(String[] nodePath) { this.nodePath = nodePath; }
}
