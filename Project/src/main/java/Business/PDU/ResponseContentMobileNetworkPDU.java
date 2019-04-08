package Business.PDU;

import Business.Enum.*;

public class ResponseContentMobileNetworkPDU extends ContentMobileNetworkPDU {

    // Data data;

    public ResponseContentMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, String contentID) {
        super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID, contentID);
    }
}
