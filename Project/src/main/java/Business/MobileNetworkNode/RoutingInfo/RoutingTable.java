package Business.MobileNetworkNode.RoutingInfo;

import Business.Utils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static Business.MobileNetworkNode.RoutingInfo.RoutingTableEntry.EntryTypes.NULL_ENTRY;

public class RoutingTable implements Map<String, RoutingTableEntry>, Serializable {

    private String ownerID;
    private Integer routingTableHash; // hash of the most recent version of the routing table, used so peers can know if they already know about it or not
    private Map<String, RoutingTableEntry> contentRoutingTable; // Key is file hash

    public RoutingTable(String ownerID) {
        this.ownerID = ownerID;
        this.contentRoutingTable = new HashMap<>();
        this.routingTableHash = 0;
    }

    public void addOwnedReference(File file) throws IOException, NoSuchAlgorithmException {
        String fileHash = Utils.hashFile(file, "md5");
        contentRoutingTable.put(fileHash,
                new RoutingTableEntry(file.getName(),
                        ownerID,
                        NULL_ENTRY.toString(),
                        0));
    }

    public void addReference(String fileHash, RoutingTableEntry entry) {
        contentRoutingTable.put(
                fileHash,
                new RoutingTableEntry(
                        entry.getFileName(),
                        entry.getDstMAC(),
                        entry.getNextHopMAC(),
                        entry.getHopCount()));
    }

    public int joinTable(RoutingTable table) {
        int changeCount = 0;
        String peerID = table.getOwnerID();
        Map<String, RoutingTableEntry> peerContentRoutingTable = table.getContentRoutingTable();

        for (Map.Entry<String, RoutingTableEntry> entry : peerContentRoutingTable.entrySet()) {
            boolean isChangesMade = false;
            String fileHash = entry.getKey();
            RoutingTableEntry tableEntry = entry.getValue();

            // Ignore entries where I am the destination in question (somewhat of a local split-horizon)
            if (!tableEntry.getDstMAC().equals(ownerID)) {

                if (!contentRoutingTable.containsKey(fileHash)) {
                    isChangesMade = true;

                } else {
                    /**
                     * A new line already exists in the table refering to that file hash...
                     * How do we conclude that the proposed new one does not bring new information?
                     * Keep in mind that we're comparing entries from different tables:
                     * - existingEntry is from this node's frame of reference
                     * - tableEntry is from the frame of reference of the node that announced this table to me
                     * At the moment, the strategy is to keep a single reference to a file (because in the future, fileHash will be the hash of a chunk, not the entire file).
                     * As such, we update the existing one if it is referencing the same next hop, but with new information
                     * Either if the destination is new, the next hop is new, the file name is new, or the hops to get there have changed
                     * So if a single one is true, we deem worthy to update
                     **/
                    RoutingTableEntry existingEntry = contentRoutingTable.get(fileHash);
                    boolean equalDestinations = tableEntry.getDstMAC().equals(existingEntry.getDstMAC());
                    boolean equalNextHop = peerID.equals(existingEntry.getNextHopMAC());
                    boolean equalFileName = tableEntry.getFileName().equals(existingEntry.getFileName());
                    boolean equalHopCount = (tableEntry.getHopCount() + 1) == existingEntry.getHopCount(); // Why + 1? The existing table references THIS one, whereas the received table to merge references the neightbour node

                    // We consider updating the table regarding a file hash if
                    isChangesMade = (equalNextHop // the new entry references the SAME neighbour (We choose to have only a single neighbour updating us on a file)
                                     && (!equalDestinations || !equalFileName || !equalHopCount)); // and something about the entry changes
                }

                if (isChangesMade) {
                    changeCount++;

                    this.addReference(fileHash,
                            new RoutingTableEntry(tableEntry.getFileName(), // File name is the same
                                    tableEntry.getDstMAC(),   // Destination is the same
                                    peerID,                   // The next hop is not the next hop of peer that gave me the table, but that peer itself
                                    1 + tableEntry.getHopCount())); // The number of hops is incremented because a neighbour gave me HIS table, so mine is 1+ away to destination
                }
            }
        }

        // Recalculate the map's hash is anything changed
        if (changeCount > 0) {
            routingTableHash = contentRoutingTable.hashCode();
        }

        return changeCount;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("owner: " + ownerID + ",\n\n");
        contentRoutingTable.entrySet().forEach(
                set -> sb.append(set.getKey() + " -> " + set.getValue().toString() + "\n")
        );
        sb.append("\n");
        sb.append("}");

        return sb.toString();
    }

    public List<Map.Entry<String,RoutingTableEntry>> similarFileNameSearch(String queryString) {
        String[] formatedSpaceSeparatedParams = queryString.split(" ");
        for (int i = 0; i < formatedSpaceSeparatedParams.length; i++) {
            formatedSpaceSeparatedParams[i] = formatedSpaceSeparatedParams[i].toLowerCase();
        }

        // For every file name, look how many matches of "spaceSeparatedParams" we get
        Function<RoutingTableEntry, Function<String[], Integer>> wordsInCommon =
                entry -> words -> {
                    int count = 0;
                    String fileName = entry.getFileName();
                    for (int i = 0; i < words.length; i++) {
                        if (fileName.toLowerCase().indexOf(words[i]) != -1) {
                            count++;
                        }
                    }
                    return count;
                };

        return contentRoutingTable.entrySet()
                .stream()
                .filter(entry -> wordsInCommon.apply(entry.getValue()).apply(formatedSpaceSeparatedParams) > 0) // Null matches get removed
                .sorted(Comparator.comparingInt(entry -> wordsInCommon.apply(entry.getValue()).apply(formatedSpaceSeparatedParams))) // Prioritize bigger matches, like on a search engine
                .collect(Collectors.toList());
    }

    public void removePeer(String peerID) {

        // Removing any mention of this peer implies removing table entries where the peer is either destination or next hop
        Predicate<RoutingTableEntry> isPeerInvolved = entry -> entry.getNextHopMAC().equals(peerID) || entry.getDstMAC().equals(peerID);

        contentRoutingTable.entrySet().removeIf(entry -> isPeerInvolved.test(entry.getValue()));
    }

    public Integer getMostRecentHash() {
        return routingTableHash;
    }

    public String getOwnerID() {
        return ownerID;
    }

    public Map<String, RoutingTableEntry> getContentRoutingTable() {
        return contentRoutingTable;
    }

    // MAP METHODS

    @Override
    public int size() {
        return contentRoutingTable.size();
    }

    @Override
    public boolean isEmpty() {
        return contentRoutingTable.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return contentRoutingTable.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return contentRoutingTable.containsValue(o);
    }

    @Override
    public RoutingTableEntry get(Object o) {
        return contentRoutingTable.get(o);
    }

    @Override
    public RoutingTableEntry put(String s, RoutingTableEntry mobileRoutingTableEntry) {
        return contentRoutingTable.put(s, mobileRoutingTableEntry);
    }

    @Override
    public RoutingTableEntry remove(Object o) {
        return contentRoutingTable.remove(o);
    }

    @Override
    public void putAll(Map<? extends String, ? extends RoutingTableEntry> map) {
        contentRoutingTable.putAll(map);
    }

    @Override
    public void clear() {
        contentRoutingTable.clear();
    }

    @Override
    public Set<String> keySet() {
        return contentRoutingTable.keySet();
    }

    @Override
    public Collection<RoutingTableEntry> values() {
        return contentRoutingTable.values();
    }

    @Override
    public Set<Entry<String, RoutingTableEntry>> entrySet() {
        return contentRoutingTable.entrySet();
    }

}
