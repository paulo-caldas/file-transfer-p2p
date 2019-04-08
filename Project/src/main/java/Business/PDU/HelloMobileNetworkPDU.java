package Business.PDU;

import Business.ContentReference;
import Business.Enum.MobileNetworkMessageType;
import Business.Enum.MobileNetworkErrorType;

import java.util.List;
import java.util.Map;

public class HelloMobileNetworkPDU extends MobileNetworkPDU {

    Map<String, List<ContentReference>> contentMap;

    public HelloMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, Map<String, List<ContentReference>> contentMap) {
        super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID);
        this.contentMap = contentMap;
    }
}
