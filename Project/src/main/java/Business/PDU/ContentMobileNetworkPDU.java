package Business.PDU;

import Business.Enum.MobileNetworkMessageType;
import Business.Enum.MobileNetworkErrorType;


import java.util.List;


public class ContentMobileNetworkPDU extends MobileNetworkPDU {
    String contentID;
    List<String> nodePath;

    public ContentMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, String contentID) {
            super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID);

    }
}
