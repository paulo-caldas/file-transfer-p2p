package main.java.PDU;

import main.java.Enum.*;

public class RequestContentMobileNetworkPDU extends ContentMobileNetworkPDU {
    public RequestContentMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, String contentID) {
        super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID, contentID);
    }
}
