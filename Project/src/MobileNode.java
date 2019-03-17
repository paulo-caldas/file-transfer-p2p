import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class MobileNode {
    private String macAddr;
    private List<String> peerMacAddrs;

    public MobileNode() {
        try {
            this.macAddr = macByteArrToString(NetworkInterface.getNetworkInterfaces().nextElement().getHardwareAddress());
            System.out.println(this.macAddr);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        this.peerMacAddrs = new ArrayList<>();
    }

    public MobileNode(String macAddr, List<String> peerList) {
        this.macAddr = macAddr;
        this.peerMacAddrs = peerList;
    }

    private String macByteArrToString(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i],
                    (i < mac.length - 1) ? ":" : ""));
        }

        return sb.toString();
    }

    public void sendHelloMessage(String dstMAC) {

    }

    public void sendPingMessage(String dstMac) {

    }

    public void sendPongMessage(String dstMac) {

    }

    // Query if my peers are alive or not by sending pings and awaiting for pongs
    public void queryPeers() {

    }

    public void addPeer(String peerMAC) {

    }

    public void run() {

        sendHelloMessage(HardwareAddressType.MULTICAST.toString());

        // Be idle for incoming messages

    }
}
