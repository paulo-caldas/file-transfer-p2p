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

    private long currentTableVersion;
    private String ownerID;
    private Map<String, RoutingTableEntry> contentRoutingTable; // Key is file hash

    public RoutingTable(String ownerID) {
        this.currentTableVersion = System.currentTimeMillis();
        this.ownerID = ownerID;
        this.contentRoutingTable = new HashMap<>();
    }

    public void addOwnedReference(File file) throws IOException, NoSuchAlgorithmException {
        String fileHash = Utils.hashFile(file, "md5");
        System.out.println("adding : " + fileHash + file.getName() + ownerID);
        contentRoutingTable.put(fileHash,
                new RoutingTableEntry(file.getName(),
                        ownerID,
                        NULL_ENTRY.toString(),
                        0,
                        currentTableVersion));
    }

    public void addReference(String fileHash, String fileName, String destination, String nextHop, Integer hopCount) {
        contentRoutingTable.put(
                fileHash,
                new RoutingTableEntry(
                        fileName,
                        destination,
                        nextHop,
                        hopCount,
                        currentTableVersion
                ));
    }

    public Long getMostRecentEntryVersionOfPeer(String peerID) {

        Predicate<RoutingTableEntry> peerIsNextHop = entry -> entry.getNextHopMAC().equals(peerID);

        return contentRoutingTable.values()
                .stream()
                .filter(peerIsNextHop)
                .map(RoutingTableEntry::getVersion)
                .max(Long::compareTo)
                .orElse(-1L);
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

    public void setVersion(long version) {
        this.currentTableVersion = version;
    }

    public void incVersion() {
        this.currentTableVersion++;
    }

    public long getCurrentTableVersion() {
        return currentTableVersion;
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
