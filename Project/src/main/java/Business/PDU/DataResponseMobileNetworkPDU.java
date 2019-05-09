package Business.PDU;

import java.io.Serializable;
import java.util.Stack;

import static Business.PDU.MobileNetworkPDU.MobileNetworkMessageType.RESPONSE_CONTENT;

/**
 * Specification of a PDU that is to be used when sending something as a response to a request
 */
public class DataResponseMobileNetworkPDU extends DataMobileNetworkPDU implements Serializable {

    // Content itself
    private Object content;

    public DataResponseMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkErrorType errorCode, int TTL, String sessionID, Stack<String> nodePath, ContentType contentType, String[] optionalParams, Object content) {
        super(srcMAC, dstMAC, RESPONSE_CONTENT, errorCode, TTL, sessionID, nodePath, contentType, optionalParams);
        this.content = content;
    }

    public Object getContent() {
        return this.content;
    }
}
