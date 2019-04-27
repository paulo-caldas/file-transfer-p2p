package Business.MobileNetworkNode.Daemon;

import Business.MobileNetworkNode.PeerKeepaliveTable;
import Business.MobileNetworkNode.MobileNode;
import org.apache.log4j.Logger;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;

public class MobileNodeKeepaliveDaemon implements MobileNodeDaemon {
    private boolean finished;
    private MobileNode representativeNode;
    private PeerKeepaliveTable keepaliveTable;
    private final int KEEPAWAY_TIME_MS = 5000;
    private final int HELLO_FLOODING_PERIODICITY = 3; // every 3 KEEPARAY_TIME_MS, flood with HELLO
    private Logger LOGGER;

    public MobileNodeKeepaliveDaemon(MobileNode representativeNode) {
        this.finished = false;
        this.representativeNode = representativeNode;
        this.keepaliveTable = representativeNode.getPeerKeepaliveTable();
        this.LOGGER = representativeNode.getLogger();
    }

    @Override
    public void run () {
        System.out.println("- Starting keepalive daemon");
        queryPeers();
    }

    @Override
    public void finish() {
        finished = true;
    }

    void queryPeers() {

        int period = 0;

        try {
            while (!finished) {
                pingAllPeers();
                Thread.sleep(KEEPAWAY_TIME_MS);
                removeDeadPeers();

                if (period++ == HELLO_FLOODING_PERIODICITY) {
                    sendHelloFlood();
                    period = 0;
                }

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void pingAllPeers() {
        String timestampOfNow = new Timestamp(Calendar.getInstance().getTime().getTime()).toString();
        synchronized (keepaliveTable) {
            keepaliveTable.setCurrentKeepaliveSessionID(timestampOfNow);
            keepaliveTable.getPeers().forEach(peer -> representativeNode.sendPingMessage((String) peer, timestampOfNow));
        }
    }

    private void removeDeadPeers() {
        List<String> removedPeers;
        synchronized (keepaliveTable) {
            removedPeers = keepaliveTable.applyStrikeWave();
        }
        LOGGER.info("Removed peers: " + removedPeers.toString());
    }

    private void sendHelloFlood() {
        representativeNode.sendHelloMessage(MobileNode.AddressType.LINK_MULTICAST.toString());
    }
}


