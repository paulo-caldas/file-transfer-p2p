package Business.PDU;

public class RequestContentMobileNetworkPDU extends ContentMobileNetworkPDU {

    public RequestContentMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, String[] nodePath) {
        super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID, nodePath);
    }
}
