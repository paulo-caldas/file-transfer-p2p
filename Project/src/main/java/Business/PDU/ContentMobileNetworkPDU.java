package PDU;

import java.util.List;
import Enum.*;

public class ContentMobileNetworkPDU extends MobileNetworkPDU {
    String contentID;
    List<String> nodePath;

    public ContentMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, String contentID) {
            super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID);

    }
}
