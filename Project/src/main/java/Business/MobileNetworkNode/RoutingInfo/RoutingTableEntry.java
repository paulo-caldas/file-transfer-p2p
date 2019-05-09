package Business.MobileNetworkNode.RoutingInfo;

import java.io.Serializable;
import java.util.Set;

import static Business.MobileNetworkNode.RoutingInfo.RoutingTableEntry.EntryTypes.NULL_ENTRY;

/**
 * A line in the Routing Table
 */
public class RoutingTableEntry implements Serializable , Comparable<RoutingTableEntry> {

    public enum EntryTypes {
        NULL_ENTRY
    }

    // Name of the file (plus extension)
    private String fileName;

    // Final destination, aka node that has the content
    private String dstMAC;

    // Address of node to forward the request to
    private String nextHopMAC;

    // Number of hops until destination is reached
    private int hopCount;

    // All the nodes that participate in allowing this path
    private Set<String> participants;

    /**
     * More columns can be added here, depending on the project's needs
     * for example: connection time, connection quality...
     */


    /**
     * Constructor
     * @param fileName Name of file
     * @param dstMAC Address of final node
     * @param nextHopMAC Address of immediate node
     * @param hopCount Number of hops to destinaion
     */
    public RoutingTableEntry(String fileName, String dstMAC, String nextHopMAC, int hopCount, Set<String> participants) {
        this.fileName = fileName;
        this.dstMAC = dstMAC;
        this.nextHopMAC = nextHopMAC;
        this.hopCount = hopCount;
        this.participants = participants;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDstMAC() {
        return dstMAC;
    }

    public void setDstMAC(String dstMAC) {
        this.dstMAC = dstMAC;
    }

    public String getNextHopMAC() {
        return nextHopMAC;
    }

    public void setNextHopMAC(String nextHopMAC) {
        this.nextHopMAC = nextHopMAC;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }

    public Set<String> getParticipants() {
        return participants;
    }

    public void setParticipants (Set<String> participants) {
        this.participants = participants;
    }

    /**
     * Say your neighbour shares his table entry, and it says:
     *
     * "I know how to reach x, next hop y, and it takes z hops"
     *
     * If we want to add this information to OUR table, we need to transform it into:
     *
     * "I know how to reach X, next hop [peer that gave me this info], and it takes z+1 hops"
     *
     * this method applies that transformation to a table entry
     */
    public void transformNeighbourEntryIntoMine(String peerID, String myID) {
        // Filename stays the same
        // Destination stays the same

        // Next hop is now the peer that gave me the entry
        this.nextHopMAC = peerID;

        // Increment hop count
        this.hopCount++;

        // Add me to the list of participants
        this.participants.add(myID);
    }

    public boolean envolvesPeer(String peerID) {
        return this.nextHopMAC.equals(peerID) || this.dstMAC.equals(peerID) || participants.contains(peerID);
    }

    @Override
    public String toString() {
        return "(" + fileName + ": (next:" + (nextHopMAC.equals(NULL_ENTRY.toString()) ? "-" : nextHopMAC.substring(15,17)) + ",dst:" + dstMAC.substring(15,17) + "(" + hopCount + " hops))";
    }

    @Override
    public boolean equals(Object obj) {
        // Two lines in a table are equals if all their contents are the same!
        if (obj == null) {
            return false;
        }

        if (!RoutingTableEntry.class.isAssignableFrom(obj.getClass())) {
            return false;
        }

        final RoutingTableEntry other = (RoutingTableEntry) obj;

        return (this.getFileName().equals(other.getFileName())
              && (this.getNextHopMAC().equals(other.getNextHopMAC()))
              && (this.getDstMAC().equals(other.getDstMAC()))
              && (this.getHopCount() == other.getHopCount()));
    }

    @Override
    public int compareTo(RoutingTableEntry other) {
        if (this.equals(other)) {
            return 0;
        } else {
           int d;

            d = this.getFileName().compareTo(other.getFileName());

           if (d == 0) {
               d = Integer.compare(this.getHopCount(), other.getHopCount());
           }

           if (d == 0) {
               d = this.getDstMAC().compareTo(other.getDstMAC());
           }

           if (d == 0) {
               d = this.getNextHopMAC().compareTo(other.getNextHopMAC());
           }

           return d;
        }
    }
}

