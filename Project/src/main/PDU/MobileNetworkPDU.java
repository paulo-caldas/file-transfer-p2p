package main.PDU;

import main.Enum.*;

import java.io.Serializable;

public class MobileNetworkPDU implements Serializable {

    private String srcMAC;
    private String dstMAC;
    private MobileNetworkMessageType messageType;
    private MobileNetworkErrorType errorCode;
    private int TTL;
    private String sessionID;

    public MobileNetworkPDU(String srcMAC, String dstMAC, MobileNetworkMessageType messageType, MobileNetworkErrorType errorCode, int TTL, String sessionID) {
        this.srcMAC = srcMAC;
        this.dstMAC = dstMAC;
        this.messageType = messageType;
        this.errorCode = errorCode;
        this.TTL = TTL;
        this.sessionID = sessionID;
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

    public String toString() {
        return String.format("(%s,%s,%s,%s,%d,%s)", srcMAC, dstMAC, messageType, errorCode, TTL, sessionID);
    }

}
