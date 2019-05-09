package Business.PDU;

import java.io.Serializable;
import java.util.Stack;

/**
 * Abstract specification of what every PDU in a mobile network should have
 */
public abstract class MobileNetworkPDU implements Serializable {

    /**
     * Types of errors
     */
    public enum MobileNetworkErrorType {
        VALID // Nothing bad happened
    }

    /**
     * Types of messages
     */
    public enum MobileNetworkMessageType {
        REQUEST_CONTENT,
        RESPONSE_CONTENT
    }

    /**
     * Types of content that can circulate in the network
     */
    public enum ContentType {
        ROUTING_TABLE,
        ROUTING_TABLE_VERSION,
        ROUTING_TABLE_INSERTION_UPDATE,
        ROUTING_TABLE_DELETION_UPDATE,
        FILE // Anything that can be converted to a byte array
    }

    public static final int STANDARD_TTL = 62;

    private String srcMAC; // Message source
    private String dstMAC; // message destination
    private MobileNetworkMessageType messageType; // Message type
    private MobileNetworkErrorType errorCode; // Error code
    private int TTL; // Max number of hops allowed (preventing of infinite stay in network
    private String sessionID; // Identifier of transaction (perhaps a timestamp)

    // Push into this array the next hop (if its a request
    // Pop from array and send to array's tail (if it's a reply)
    //
    // A --- [A,B] ---> B --- [A,B,C] ---> C
    //           ...
    // A <--- [A] --- B <--- [A,B] <--- C
    private Stack<String> nodePath;

    public MobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID, Stack<String> nodePath) {
        this.srcMAC = srcMAC;
        this.dstMAC = dstMAC;
        this.messageType = messageType;
        this.errorCode = errorCode;
        this.TTL = TTL;
        this.sessionID = sessionID;
        this.nodePath = nodePath;
    }

    public String getSrcMAC() {
        return srcMAC;
    }

    public String getDstMAC() {
        return dstMAC;
    }

    public MobileNetworkMessageType getMessageType() {
        return messageType;
    }

    public MobileNetworkErrorType getErrorCode() {
        return errorCode;
    }

    public int getTTL() {
        return TTL;
    }

    public String getSessionID() {
        return sessionID;
    }

    public Stack<String> getNodePath() { return nodePath; }

    @Override
    public String toString() {
        return String.format("(from:%s,to:%s,%s,%s,%d,%s,%s)", srcMAC, dstMAC, messageType, errorCode, TTL, sessionID, nodePath.toString());
    }
}
