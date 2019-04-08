package main.PDU;

import main.ContentReference;
import main.Enum.MobileNetworkMessageType;
import main.Enum.MobileNetworkErrorType;

import java.util.List;
import java.util.Map;

public class HelloMobileNetworkPDU extends MobileNetworkPDU {

    Map<String, List<ContentReference>> contentMap;

    public HelloMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, Map<String, List<ContentReference>> contentMap) {
        super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID);
        this.contentMap = contentMap;
    }
}
