package Business.MobileNetworkNode.Daemon;

import Business.MobileNetworkNode.DataCache.FileFragment;
import Business.MobileNetworkNode.MobileNode;
import Business.MobileNetworkNode.RoutingInfo.RoutingTable;
import Business.MobileNetworkNode.RoutingInfo.RoutingTableEntry;
import Business.PDU.DataRequestMobileNetworkPDU;
import Business.PDU.DataResponseMobileNetworkPDU;
import Business.PDU.MobileNetworkPDU;
import Business.PDU.MobileNetworkPDU.MobileNetworkMessageType;
import Business.PDU.MobileNetworkPDU.ContentType;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;

import static Business.MobileNetworkNode.MobileNode.AddressType.LINK_BROADCAST;
import static Business.Utils.*;

/**
 * Daemon responsible for, in behalf of the mobile node, listen for incoming messages
 * and reply appropriately, as defined by the protocol
 */
public class MobileNodeListeningDaemon extends MobileNodeDaemon {
    private final Logger LOGGER;

    public MobileNodeListeningDaemon(MobileNode representativeNode) {
        super(representativeNode);
        this.LOGGER = representativeNode.getLogger();
    }

    @Override
    public void run() {
        LOGGER.debug("Starting Listening daemon");
        listenForPeers();
        LOGGER.debug("Terminating Listening daemon");
    }

    @Override
    public void finish() {
        super.finish();
        super.stopListening(); // Closing is necessary to end blocking method "receive"
    }

    private void listenForPeers() {
        LOGGER.debug("Listening for peers");

        while(!super.isFinished()) {
            try {
                // Await for a connection to be made, and parse it as a PDU object
                MobileNetworkPDU pdu = super.capturePDU();

                // Figure out what to do with it
                parsePacket(pdu);
            } catch (ClassNotFoundException e) {
                LOGGER.error("Object captured in socket was not of type MobileNetworkPDU");
            } catch (IOException e) {
                if (super.isFinished()) {
                    LOGGER.info("Signaled to stop listening");

                } else {
                    LOGGER.error("IOException whilst listening for packet in socket\n");
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     *  ======================== Parsing
     */

    private void parsePacket(MobileNetworkPDU pdu) {
        // Retrieve important variables that dictate what to do next
        String msgSource = pdu.getSrcMAC();

        boolean isMessageCreatedByMe = msgSource.equals(super.getNodeMacAdress());

        // Only proceed if the message is not originated by me
        // (this guard is a necessity because multicast messages get forwarded back to me)
        if (!isMessageCreatedByMe) {
            MobileNetworkMessageType messageType = pdu.getMessageType();

            switch (messageType) {
                case REQUEST_CONTENT:
                    // A request of what? Parse it
                    parseRequestContentPacket((DataRequestMobileNetworkPDU) pdu);
                    break;
                case RESPONSE_CONTENT:
                    // A response of what? Parse it
                    parseResponseContentPacket((DataResponseMobileNetworkPDU) pdu);
                    break;
                default:
                    break;
            }
        }
    }

    private void parseRequestContentPacket(DataRequestMobileNetworkPDU pdu) {
        String messageDestination = pdu.getDstMAC();

        boolean amIDestination = messageDestination.equals(super.getNodeMacAdress())
                || messageDestination.equals(LINK_BROADCAST.toString());

        if (amIDestination) {
            // I am the final destination of this request
            respondToDataRequest(pdu);
        } else {
            Stack<String> nodePath = pdu.getNodePath();
            String nextHop = nodePath.peek();

            boolean amIIntermediate = (nextHop != null) && nextHop.equals(super.getNodeMacAdress());

            if (amIIntermediate) {
                // The destination is somebody else, I am simply an intermediate
                super.forwardRequestPacket(pdu);
            }
        }
    }

    private void parseResponseContentPacket(DataResponseMobileNetworkPDU responsePDU) {
        String messageDestination = responsePDU.getDstMAC();
        boolean amIDestination = messageDestination.equals(super.getNodeMacAdress())
                || messageDestination.equals(LINK_BROADCAST.toString());

        if (amIDestination) {
            // I am the destination of this content
            processData(responsePDU);

        } else {
            // Check if I am the next hop to the message
            Stack<String> nodePath = responsePDU.getNodePath();
            String nextHop = nodePath.peek();

            boolean amIIntermediate = (nextHop != null) && nextHop.equals(super.getNodeMacAdress());

            if (amIIntermediate) {
                // I am an intermediate hop to the final destination
                super.forwardResponsePacket(responsePDU);
            }
        }
    }

    /**
     *  ======================== Responding to request messages
     */

    private void respondToDataRequest(DataRequestMobileNetworkPDU pdu) {
        ContentType contentType = pdu.getContentType();

        switch (contentType) {
            case ROUTING_TABLE_VERSION:
                // Requesting for the version of a node is essentially a ping
                respondToPing(pdu);
                break;
            case ROUTING_TABLE:
                respondToRoutingTableRequest(pdu);
                break;
            case FILE:
                respondToFileRequest(pdu);
                break;
            default:
                // TODO: send error of unknown type
                break;
        }
    }

    private void respondToPing(MobileNetworkPDU pdu) {
        LOGGER.debug("Received(PING): " + pdu.toString());

        String peerID = pdu.getSrcMAC();

        boolean alreadyKnowPeer = super.isPeerUnderMonitoring(peerID);

        if (alreadyKnowPeer) {
            super.sendPongMessage(pdu.getSrcMAC(), pdu.getSessionID(), super.getTableVersion());
        } else {
            LOGGER.info("Received ping from unknown host");
            super.sendHelloRequest(pdu.getSrcMAC(), getTimestampOfNow());
        }
    }

    private void respondToRoutingTableRequest(DataRequestMobileNetworkPDU requestPDU) {
        LOGGER.debug("Received(REQUEST_HELLO): " + requestPDU.toString());

        super.sendHelloMessage(requestPDU.getSrcMAC(), getTimestampOfNow());
    }

    private void respondToFileRequest(DataRequestMobileNetworkPDU requestPDU) {
        LOGGER.debug("Received(REQUEST_FILE): " + requestPDU.toString());

        String[] params = requestPDU.getParams();

        if (params.length == 0) {
            // TODO: Send error: insufficient params

            return;
        }

        String fileHashRequested = params[0];

        // If the user doesn't explicitly use the second param to ask for a specific byte, we assume he asks for the first one (index 0)
        int initByteRequested = params.length == 2 ? Integer.parseInt(params[1]) : 0;

        try {
            FileFragment requestedFragment = super.getFragment(fileHashRequested, initByteRequested);

            super.sendResponseFileMessage(requestPDU, requestedFragment, params);
        } catch (NullPointerException e) {
            // TODO: Send error: Non-existent content
        }
    }

    /**
     *  ======================== Processing responses
     */

    private void processHelloPacket(DataResponseMobileNetworkPDU helloPDU) {
        LOGGER.debug("Received(HELLO): " + helloPDU.toString());

        // Retrieve important info from packet
        RoutingTable peerRoutingTable = (RoutingTable) helloPDU.getContent();
        String peerTableVersion = peerRoutingTable.getVersion();
        String peerID = helloPDU.getSrcMAC();

        // Conclude about how well I know this peer
        boolean isMyself = peerID.equals(super.getNodeMacAdress());
        boolean isNewPeer = (!isMyself && !super.isPeerUnderMonitoring(peerID));

        // Only bother updating information if what I received is not something I already know about
        if (!isMyself && isNewPeer) {
            LOGGER.info("HELLO message that was received incurred in a new routing table");

            // Use the peers routing table to tell back to the peers to update theirs
            // previousVersion => Most updated version my peers have of my table
            // version => New version of my table (so they can cache it and get new updates)
            peerRoutingTable.setPreviousVersion(super.getTableVersion());

            // Remove entries that are about me (As a split horizon)
            peerRoutingTable.removeTotalPeerReference(super.getNodeMacAdress());

            // Transform all paths into what they should be in MY perspective
            peerRoutingTable.transformIntoMyPerspective(peerID, super.getNodeMacAdress());

            // Take the peer's routing table and merge it with my own (this changes the table version)
            boolean changesMade = super.addEntries(peerRoutingTable);

            peerRoutingTable.setVersion(super.getTableVersion());

            // Mark peer as alive, because we received an hello after all
            super.markPeerAsAlive(peerID);

            // Remember what the most recent change related to that peer was (so next time isUpdatedTable = false)
            super.cacheMostRecentPeerReference(peerID, peerTableVersion);

            if (changesMade) {
                // New changes were made, send them out to peers
                super.sendInsertUpdate(peerRoutingTable, getTimestampOfNow());
            }
        } else {
            LOGGER.info("HELLO message that was received had no new information");
        }
    }

    private void processData(DataResponseMobileNetworkPDU responsePDU) {
        ContentType contentType = responsePDU.getContentType();

        switch (contentType) {
            case ROUTING_TABLE_VERSION:
                // The reply of a table version constitutes a response to a ping request, thus essentially a pong
                processPong(responsePDU);
                break;
            case ROUTING_TABLE:
                processHelloPacket(responsePDU);
                break;
            case FILE:
                processFileResponse(responsePDU);
                break;
            case ROUTING_TABLE_DELETION_UPDATE:
                processDeletionUpdate(responsePDU);
                break;
            case ROUTING_TABLE_INSERTION_UPDATE:
                processInsertionUpdate(responsePDU);
                break;
            default:
                // TODO: send error of unknown type
                break;
        }
    }

    private void processDeletionUpdate(DataResponseMobileNetworkPDU responsePDU) {
        LOGGER.debug("Received(UPDT_REM): " + responsePDU.toString());
        String peerID = responsePDU.getSrcMAC();
        RoutingTable pathsToRemove = (RoutingTable) responsePDU.getContent();

        // This is a direct update of a table I know
        String versionOfPeerTableAfterRemovals = pathsToRemove.getVersion();
        pathsToRemove.setPreviousVersion(super.getTableVersion());

        // Remove entries that are about me (As a split horizon)
        pathsToRemove.removeTotalPeerReference(super.getNodeMacAdress());

        // Transform all paths into what they should be in MY perspective
        pathsToRemove.transformIntoMyPerspective(peerID, super.getNodeMacAdress());

        boolean newChanges = super.deleteEntries(pathsToRemove, peerID);
        pathsToRemove.setVersion(super.getTableVersion());

        // Signal who i got it from
        pathsToRemove.setOwner(peerID);

        super.cacheMostRecentPeerReference(peerID, versionOfPeerTableAfterRemovals);

        if (newChanges) {
            super.sendRemoveUpdate(pathsToRemove, getTimestampOfNow());
        }
    }

    private void processInsertionUpdate(DataResponseMobileNetworkPDU responsePDU) {
        LOGGER.debug("Received(UPDT_INSRT): " + responsePDU.toString());
        String peerID = responsePDU.getSrcMAC();
        RoutingTable pathsToAdd = (RoutingTable) responsePDU.getContent();

        String versionOfPeerTableAfterAddition = pathsToAdd.getVersion();

        pathsToAdd.setPreviousVersion(super.getTableVersion()); // This is done so

        // Remove entries that are about me (As a split horizon)
        pathsToAdd.removeTotalPeerReference(super.getNodeMacAdress());

        // Transform all paths into what they should be in MY perspective
        pathsToAdd.transformIntoMyPerspective(peerID, super.getNodeMacAdress());

        boolean newChanges = super.addEntries(pathsToAdd);

        pathsToAdd.setVersion(super.getTableVersion());

        pathsToAdd.setOwner(peerID); // To signal who I got this from

        super.cacheMostRecentPeerReference(peerID, versionOfPeerTableAfterAddition);

        if (newChanges) {
            super.sendInsertUpdate(pathsToAdd, getTimestampOfNow());
        }

    }


    private void processPong(DataResponseMobileNetworkPDU responsePDU) {
        LOGGER.debug("Received(PONG): " + responsePDU.toString());

        String peerID = responsePDU.getSrcMAC();
        String sessionID = responsePDU.getSessionID();

        if (super.isPeerUnderMonitoring(peerID)) {
            boolean isPingRecent;

            // Attempting to mark a peer as alive return a boolean of weather or not it was succeeded
            // It doesn't succeed if the user did not send a pong back in time
            isPingRecent = super.markPeerAsAlive(sessionID, peerID);

            if (isPingRecent) {
                LOGGER.debug("Marked peer " + peerID + " as alive (received updated PONG)");
                String tableVersion = (String) responsePDU.getContent();
                String cachedVersion = super.getMostRecentPeerReferenceOrDefault(peerID, "-1");

                if (!tableVersion.equals(cachedVersion)) {
                    // I found out that the version I received is not updated. This means that I have outdated information
                    LOGGER.info("Notified that known version (" + cachedVersion +") and received version (" + tableVersion + ") are different. Removing");

                    Map<String, Set<RoutingTableEntry>> removedPaths = super.removeAllReferencesOfPeer(peerID);
                    RoutingTable removedTable = new RoutingTable(peerID, removedPaths);

                    // New changes were made, send them out to peers (similarly to distance vector protocol RIP)
                    super.sendRemoveUpdate(removedTable, getTimestampOfNow());
                }
            } else {
                LOGGER.info("Received outdated keepalive PONG from " + peerID);
            }
        } else {
            LOGGER.info("Received PONG from unknown peer " + peerID);
        }
    }

    private void processFileResponse(DataResponseMobileNetworkPDU responsePDU) {
        LOGGER.info("Received (FILE_RESPONSE): " + responsePDU.toString());
        FileFragment fileFragment = (FileFragment) responsePDU.getContent();

        String fileHash = responsePDU.getParams()[0];

        // TODO : use boolean variable to know when to tell user download is complete
        try {
            boolean isDownloadComplete = super.addFragmentToCache(fileHash, fileFragment);
        } catch (IOException e) {
            LOGGER.error("Couldn't write byte array into file");
        }
    }

    private boolean isDirectUpdate(String peerID, RoutingTable pathsToRemove) {
        String versionItRefersTo = pathsToRemove.getPreviousVersion();
        String cachedVersion = super.getMostRecentPeerReferenceOrDefault(peerID, "-1");

        return (versionItRefersTo.equals(cachedVersion));
    }
}

