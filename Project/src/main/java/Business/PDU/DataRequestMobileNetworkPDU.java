package Business.PDU;

import java.io.Serializable;
import java.util.Stack;

import static Business.PDU.MobileNetworkPDU.MobileNetworkMessageType.REQUEST_CONTENT;

/**
 * Specification of a PDU that is to be used when requesting something
 */
public class DataRequestMobileNetworkPDU extends DataMobileNetworkPDU implements Serializable {

    // No params given
    public DataRequestMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkErrorType errorCode, int TTL, String sessionID, Stack<String> nodePath, ContentType requestedContentType, String[] optionalParams) {
        super(srcMAC, dstMAC, REQUEST_CONTENT, errorCode, TTL, sessionID, nodePath, requestedContentType, optionalParams);
    }
}
