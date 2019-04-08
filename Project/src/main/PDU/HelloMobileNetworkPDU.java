package PDU;

import Enum.MobileNetworkMessageType;
import Enum.MobileNetworkErrorType;

public class HelloMobileNetworkPDU extends MobileNetworkPDU {

    Map<String, List<ContentReference>> contentMap;

    public HelloMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, Map<String, List<ContentReference>> contentMap) {
        super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID);
        this.contentMap = contentMap;
    }
}
