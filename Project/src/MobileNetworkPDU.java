public class MobileNetworkPDU {

    private byte[] srcMAC;
    private byte[] dstMAC;
    private String contentID;
    private String sessionID;
    private MobileNetworkErrorType errorCode;
    private MobileNetworkMessageType messageType;
    private int TTL;
    private int payloadLen;
    private String[] routerPath;
    private String payload; // JSON string, null if its of type ping or pong
}
