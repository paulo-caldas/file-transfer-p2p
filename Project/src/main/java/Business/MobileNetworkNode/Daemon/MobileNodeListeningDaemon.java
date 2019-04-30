package Business.MobileNetworkNode.Daemon;

import Business.MobileNetworkNode.PeerKeepaliveTable;
import Business.MobileNetworkNode.MobileNode;
import Business.MobileNetworkNode.RoutingInfo.RoutingTable;
import Business.PDU.HelloMobileNetworkPDU;
import Business.PDU.MobileNetworkPDU;
import Business.PDU.MobileNetworkPDU.MobileNetworkMessageType;
import Business.PDU.RequestContentMobileNetworkPDU;
import Business.PDU.ResponseContentMobileNetworkPDU;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketException;

import static Business.MobileNetworkNode.MobileNode.AddressType.LINK_BROADCAST;
import static Business.MobileNetworkNode.MobileNode.AddressType.LINK_MULTICAST;

/**
 * Daemon responsible for, in behalf of the mobile node, listen for incoming messages
 * and reply appropriately
 */
public class MobileNodeListeningDaemon implements MobileNodeDaemon {
    private boolean finished;
    private final MobileNode representativeNode;
    private final RoutingTable contentRoutingTable;
    private final PeerKeepaliveTable keepaliveTable;
    private final MulticastSocket receiveServerSocket;
    private DatagramPacket receivePacket;
    private final Logger LOGGER;


    public MobileNodeListeningDaemon(MobileNode representativeNode) {
        this.finished = false;
        this.representativeNode = representativeNode;
        this.contentRoutingTable = representativeNode.getRoutingTable();
        this.keepaliveTable = representativeNode.getPeerKeepaliveTable();
        this.receiveServerSocket = representativeNode.getReceiveServerSocket();
        this.receivePacket = representativeNode.getReceivePacket();
        this.LOGGER = representativeNode.getLogger();
    }

    @Override
    public void run() {
        System.out.println("- Starting listening daemon");
        listenForPeers();
    }

    @Override
    public void finish() {
        finished = true;
        receiveServerSocket.close();
    }

    private void listenForPeers() {
        LOGGER.debug("Listening for peers");
        while(!finished) {
            // Await for a connection to be made, and parse it as a PDU object
            try {
                receiveServerSocket.receive(receivePacket);
                byte[] data = receivePacket.getData();
                ByteArrayInputStream in = new ByteArrayInputStream(data);
                ObjectInputStream objectInputStream = new ObjectInputStream(in);
                MobileNetworkPDU pdu = (MobileNetworkPDU) objectInputStream.readObject();
                // Retrieve important variables that dictate what to do next
                String source = pdu.getSrcMAC();
                String destination = pdu.getDstMAC();

                // Only proceed if the message is directed towards me
                if (isNodePartOfLinkDestination(source, destination)) {

                    MobileNetworkMessageType messageType = pdu.getMessageType();

                    switch (messageType) {
                        case HELLO:
                            processHelloPacket((HelloMobileNetworkPDU) pdu);
                            break;
                        case PING:
                            processPingPacket(pdu);
                            break;
                        case PONG:
                            processPongPacket(pdu);
                            break;
                        case REQUEST_CONTENT:
                            processRequestContentPacket((RequestContentMobileNetworkPDU) pdu);
                            break;
                        case RESPONSE_CONTENT:
                            processResponseContentPacket((ResponseContentMobileNetworkPDU) pdu);
                            break;
                        default:
                            // drop
                            break;
                    }
                }
            } catch (ClassNotFoundException e) {
                LOGGER.error("Class not found");
                e.printStackTrace();
            } catch (SocketException e) {
                // Reached here when the receive socket is closed, to finish the daemon
                // Do nothing, let loop end gracefully
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processHelloPacket(HelloMobileNetworkPDU helloPDU) {
        RoutingTable peerRoutingTable = helloPDU.getRoutingTable();
        String peerID = helloPDU.getSrcMAC();

        boolean isNewPeer;
        boolean isMyself;
        boolean isUpdatedTable;

        isMyself = peerID.equals(representativeNode.getMacAddr());
        synchronized (keepaliveTable) {
            isNewPeer = !keepaliveTable.hasPeer(peerID);
        }
        synchronized (contentRoutingTable) {
            isUpdatedTable = contentRoutingTable.getMostRecentEntryVersionOfPeer(peerID) < peerRoutingTable.getCurrentTableVersion();

            if (!isMyself && (isNewPeer || isUpdatedTable)) {
                LOGGER.debug("Received: " + helloPDU.toString());

                peerRoutingTable.incVersion();
                peerRoutingTable.entrySet().forEach(
                        entry ->
                                contentRoutingTable.addReference(
                                        entry.getKey(),
                                        entry.getValue().getFileName(),
                                        entry.getValue().getDstMAC(),
                                        peerID,
                                        1 + entry.getValue().getHopCount()));
            }

            synchronized (keepaliveTable) {
                keepaliveTable.markAsAlive(peerID);
            }

            // New changes were made, send them out
            representativeNode.sendHelloMessage(LINK_MULTICAST.toString());
        }
    }

    private void processPingPacket(MobileNetworkPDU pdu) {
        LOGGER.debug("Received: " + pdu.toString());

        String peerID = pdu.getSrcMAC();
        boolean alreadyKnowPeer;

        synchronized (keepaliveTable) {
            alreadyKnowPeer = keepaliveTable.hasPeer(peerID);
        }

        if (alreadyKnowPeer) {
            representativeNode.sendPongMessage(pdu.getSrcMAC(), pdu.getSessionID());
        } else {
            synchronized (keepaliveTable) {
                keepaliveTable.markAsAlive(peerID);
            }
            representativeNode.sendHelloMessage(pdu.getSrcMAC());
        }
    }

    private void processPongPacket(MobileNetworkPDU pdu) {
        LOGGER.debug("Received: " + pdu.toString());

        String peerID = pdu.getSrcMAC();
        String sessionID = pdu.getSessionID();
        boolean isPingRecent;

        synchronized (keepaliveTable) {
            isPingRecent = keepaliveTable.markAsAlive(sessionID, peerID);
        }
        if (isPingRecent) {
            LOGGER.debug("Marked peer " + peerID + " as alive");
        } else {
            LOGGER.debug("Received outdated keepalive from " + peerID);
        }
    }

    private void processRequestContentPacket(RequestContentMobileNetworkPDU pdu) {
        String[] nodePath = pdu.getNodePath();
        String source = pdu.getSrcMAC();
        String destination = pdu.getDstMAC();

        if (isNodePartOfLinkDestination(source, destination)) {

            // The message is directed to this node on a link level

            // Now parse regarding to the node path: Am I the next hop?

            String myMAC = representativeNode.getMacAddr();

            if (nodePath[nodePath.length - 1].equals(myMAC)) {
                if(destination.equals(myMAC)) {
                    // Situation 1: I am a final next hop
                    // Create a ContentResponsePDU and set the destination to the node that requested it

                } else {
                    // Situation 2: I am an intermediate next hop
                    // Pop the node path and forward

                }
            }
        }
    }

    private void processResponseContentPacket(ResponseContentMobileNetworkPDU pdu) {

    }

    private boolean isNodePartOfLinkDestination(String source, String destination) {
        String myMacAddr = representativeNode.getMacAddr();

        // Ignore messages the node created itself
        if (source.equals(myMacAddr)) {
            return false;
        }

        // Accept being part of destination if one of the following happens:

        return ( destination.equals(myMacAddr)                                // I am explicitly the destination
                || destination.equals(LINK_BROADCAST.toString())    // It is a broadcast message
                || destination.equals(LINK_MULTICAST.toString()));  // It is a subscribed multicast message
    }
}

