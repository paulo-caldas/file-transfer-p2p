package Business.PDU;

import Business.ContentRoutingTable;
import Business.Enum.MobileNetworkMessageType;
import Business.Enum.MobileNetworkErrorType;

public class HelloMobileNetworkPDU extends MobileNetworkPDU {
    private ContentRoutingTable contentRoutingTable;

    public HelloMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, ContentRoutingTable contentRoutingTable) {
        super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID);
        this.contentRoutingTable = contentRoutingTable;
    }

    public ContentRoutingTable getContentRoutingTable() {
        return contentRoutingTable;
    }

}
