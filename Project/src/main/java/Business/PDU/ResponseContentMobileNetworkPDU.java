package Business.PDU;

public class ResponseContentMobileNetworkPDU extends ContentMobileNetworkPDU {

    // Data data;
    String data;

    public ResponseContentMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, String[] routerPath, String data) {
        super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID, routerPath);
        this.data = data;
    }

    public String getData() { return this.data; }
    public void setData(String data) { this.data = data; }
}
