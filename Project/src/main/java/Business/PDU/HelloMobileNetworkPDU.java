package Business.PDU;

import Business.MobileNetworkNode.RoutingInfo.RoutingTable;

public class HelloMobileNetworkPDU extends MobileNetworkPDU {
    private RoutingTable contentRoutingTable;

    public HelloMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, RoutingTable contentRoutingTable) {
        super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID);
        this.contentRoutingTable = contentRoutingTable;
    }

    public RoutingTable getRoutingTable() {
        return contentRoutingTable;
    }
}
