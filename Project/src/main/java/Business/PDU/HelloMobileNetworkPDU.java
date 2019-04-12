package Business.PDU;

import Business.Enum.MobileNetworkMessageType;
import Business.Enum.MobileNetworkErrorType;
import Business.MobileRoutingTableEntry;

import java.util.Map;

public class HelloMobileNetworkPDU extends MobileNetworkPDU {
    private Map<String, MobileRoutingTableEntry> contentRoutingTable;

    public HelloMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, Map<String, MobileRoutingTableEntry> contentRoutingTable) {
        super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID);
        this.contentRoutingTable = contentRoutingTable;
    }

    public Map<String, MobileRoutingTableEntry> getContentRoutingTable() {
        return contentRoutingTable;
    }

}
