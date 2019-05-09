package Business.PDU;

import java.util.Stack;

public abstract class DataMobileNetworkPDU extends MobileNetworkPDU {
    // Type of content being sent
    private ContentType contentType;

    // (Optional) parameters that were added in the request this message resposts to
    private String[] params; // Same concept as String[] argv

    public DataMobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, Stack<String> nodePath, ContentType contentType, String[] params) {
        super(srcMAC, dstMAC, messageType, errorCode, TTL, sessionID, nodePath);
        this.contentType = contentType;
        this.params = params;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public String[] getParams() {
        return params;
    }

}
