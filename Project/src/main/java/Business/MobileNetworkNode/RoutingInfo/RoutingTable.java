package Business.MobileNetworkNode.RoutingInfo;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static Business.MobileNetworkNode.RoutingInfo.RoutingTableEntry.EntryTypes.NULL_ENTRY;
import static Business.Utils.getTimestampOfNow;

/**
 * Class that encapsulates all the routing information necessary for a node to retrieve content
 * and communicate with peers
 */
public class RoutingTable implements Map<String, Set<RoutingTableEntry>>, Serializable {

    // ID of the node that owns this table (useful when this class gets shared with other peers)
    private String ownerID;

    // Version of the routing table (Can be timestamp, incremented version, hash, etc)
    // Useful so peers can immediately identify if they are already updated on a peer's routing info
    // Previous version is also checked so when we give someone a table and say "add this to your existing
    // table", version says the new version, and previousVersion says what version this refers to
    private String previousVersion;
    private String version;

    // Map a file's identifier (it's hash) to a list of lines in the routing table (Each line is a possible path to take)
    private Map<String, Set<RoutingTableEntry>> contentRoutingTable;

    /**
     * Constructor
     * @param ownerID ID of the owner of this routing table
     */
    public RoutingTable(String ownerID) {
        this.ownerID = ownerID;
        this.contentRoutingTable = new HashMap<>();
        this.previousVersion = getTimestampOfNow();
        this.version = this.previousVersion;
    }

    public RoutingTable(String ownedID, Map<String, Set<RoutingTableEntry>> table) {
        this.ownerID = ownedID;
        this.contentRoutingTable = table;
        this.previousVersion = "n/a";
        this.version = "n/a";
    }

    public String getVersion() {
        return version;
    }

    public String getPreviousVersion() {
        return previousVersion;
    }

    public String getOwnerID() {
        return ownerID;
    }

    public void setPreviousVersion(String version) {
        this.previousVersion = version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setOwner(String peerID) {
        this.ownerID = peerID;
    }


    public Map<String, Set<RoutingTableEntry>> getContentRoutingTable() {
        return contentRoutingTable;
    }

    /**
     * Add a reference to a file that the node owns locally
     * @param file File to add
     */
    public void addOwnedReference(String fileHash, File file) {
        boolean tableChanged = false;

        Set<RoutingTableEntry> knownPaths;

        if (contentRoutingTable.containsKey(fileHash)) {
            knownPaths = contentRoutingTable.get(fileHash);
        } else {
            knownPaths = new TreeSet<>();
        }

        Set<String> members = new HashSet<>();

        members.add(this.ownerID);

        RoutingTableEntry localTableEntry =
                new RoutingTableEntry(file.getName(),
                        ownerID,
                        NULL_ENTRY.toString(), // there is no next hop, since the file is stored locally
                        0,
                        members); // There are no hops to take, since the file is stored locally

        tableChanged |= knownPaths.add(localTableEntry);

        contentRoutingTable.put(fileHash, knownPaths);

        if (tableChanged) {
            updateVersion();
        }
    }

    public boolean addEntries(RoutingTable pathsToAdd) {

        // Ignore updates I gave to other people (redundant not to)

        if (pathsToAdd.ownerID.equals(this.ownerID)) {
            return false;
        }

        boolean tableChanged = false;

        for (Map.Entry<String, Set<RoutingTableEntry>> entry : pathsToAdd.entrySet()) {

            String fileHash = entry.getKey();
            Set<RoutingTableEntry> paths = entry.getValue();

            Set<RoutingTableEntry> knownPaths;
            if (!contentRoutingTable.containsKey(fileHash)) {
                knownPaths = new TreeSet<>();
            } else {
                knownPaths = contentRoutingTable.get(fileHash);
            }

            boolean newAdditions = knownPaths.addAll(paths);
            tableChanged |= newAdditions;

            contentRoutingTable.put(fileHash, knownPaths);
        }

        if (tableChanged) {
            updateVersion();
        }

        return tableChanged;
    }

    public RoutingTableEntry getBestHopCountEntry(String fileHash) throws NullPointerException {
        return contentRoutingTable.get(fileHash)
                .stream()
                .max(Comparator.comparingInt(RoutingTableEntry::getHopCount))
                .orElseThrow(NullPointerException::new);
    }

    public String getNextPeerHop(String destination) {
        for (Set<RoutingTableEntry> entries : contentRoutingTable.values()) {
            for (RoutingTableEntry entry : entries) {
                if (entry.getDstMAC().equals(destination)) {
                    return entry.getNextHopMAC();
                }
            }
        }

        return null;
    }

    /**
     * Get a list of routing entries where the related filename is similar to a query name
     * @param queryString String to look for
     * @return
     */
    public Map<String,Set<RoutingTableEntry>> similarFileNameSearch(String queryString) {
        // Split the querystring into space-separated words
        String[] formatedSpaceSeparatedParams = queryString.split(" ");
        for (int i = 0; i < formatedSpaceSeparatedParams.length; i++) {
            formatedSpaceSeparatedParams[i] = formatedSpaceSeparatedParams[i].toLowerCase();
        }

        // Given a routing table entry, look how many matches of "spaceSeparatedParams" we get in it's file name
        Function<RoutingTableEntry, Function<String[], Integer>> wordsInCommon =
                entry -> words -> {
                    int count = 0;
                    String fileName = entry.getFileName();
                    for (String word : words) {
                        if (fileName.toLowerCase().contains(word)) {
                            count++;
                        }
                    }
                    return count;
                };

        // From a set of entries in a routing table, retrieve only those whose file name
        // is similar to a list of words
        Function<Set<RoutingTableEntry>, Function<String[], Set<RoutingTableEntry>>> getOnlyFileNameMatches =
                entries -> words -> {
                    Set<RoutingTableEntry> matches = new TreeSet<>();

                    for (RoutingTableEntry entry : entries) {

                        // If at least one word matches
                        if (wordsInCommon.apply(entry).apply(words) > 0) {
                            matches.add(entry);
                        }
                    }

                    return matches;
                };

        Map<String, Set<RoutingTableEntry>> matches = new HashMap<>();

        for (Map.Entry<String, Set<RoutingTableEntry>> entry : contentRoutingTable.entrySet()) {
            Set<RoutingTableEntry> matchesWithSimilarName = getOnlyFileNameMatches.apply(entry.getValue()).apply(formatedSpaceSeparatedParams);

            matches.put(entry.getKey(), matchesWithSimilarName);
        }

        return matches;
    }

    public Map<String, Set<RoutingTableEntry>> removeEntriesWithNextHop(String peerID) {
        Map<String, Set<RoutingTableEntry>> removedTable = new HashMap<>();

        // Not communicating with peer anymore aka remove entries where i trust him to be next hop
        Predicate<RoutingTableEntry> isPeerInvolved = entry -> entry.getNextHopMAC().equals(peerID);

        for (Map.Entry<String, Set<RoutingTableEntry>> paths : contentRoutingTable.entrySet()) {
            String fileHash = paths.getKey();
            Set<RoutingTableEntry> allPaths = paths.getValue();
            Set<RoutingTableEntry> removedPaths = new TreeSet<>();

            for (RoutingTableEntry path : allPaths) {
                if (isPeerInvolved.test(path)) {
                    removedPaths.add(path);
                }
            }

            allPaths.removeAll(removedPaths) ;
            contentRoutingTable.put(fileHash, allPaths);

            if (removedPaths.size() > 0) {
                removedTable.put(fileHash, removedPaths);
            }
        }

        if (removedTable.size() > 0) {
            updateVersion();
        }

        return removedTable;
    }

    public void removeTotalPeerReference(String peerID) {
        List<String> removedFileHashes = new ArrayList<>();

        for (Map.Entry<String, Set<RoutingTableEntry>> entry : this.getContentRoutingTable().entrySet()) {

            String fileHash = entry.getKey();
            Set<RoutingTableEntry> paths = entry.getValue();
            paths.removeIf(path -> path.envolvesPeer(peerID));

            if (paths.size() > 0) {
                this.put(fileHash, paths);
            } else {
                removedFileHashes.add(fileHash);
            }
        }

        removedFileHashes.forEach(s -> this.getContentRoutingTable().remove(s));
    }

    public boolean removeEntries(RoutingTable removingPaths, String peerID) {
        // Ignore updates I gave to other people (redundant not to)

        if (removingPaths.ownerID.equals(this.ownerID)) {
            return false;
        }

        boolean tableChanged = false;

        for (Map.Entry<String, Set<RoutingTableEntry>> paths : removingPaths.entrySet()) {
            String fileHash = paths.getKey();

            boolean removedExistingContent = contentRoutingTable.get(fileHash).removeAll(paths.getValue());

            tableChanged |= removedExistingContent;
        }

        if (tableChanged) {
            updateVersion();
        }

        return tableChanged;
    }

    public void transformIntoMyPerspective(String peerID, String myID) {
        // Transform all paths into what they should be in MY perspective
        for (Map.Entry<String, Set<RoutingTableEntry>> entry : this.getContentRoutingTable().entrySet()) {
            String fileHash = entry.getKey();
            Set<RoutingTableEntry> paths = entry.getValue();
            paths.forEach(path -> path.transformNeighbourEntryIntoMine(peerID, myID));
            this.put(fileHash, paths);
        }
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

    public void updateVersion() {
        previousVersion = version;
        version = getTimestampOfNow();
    }

    /**
     * =================== Map related interface implementations
     */

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
    public Set<RoutingTableEntry> get(Object o) {
        return contentRoutingTable.get(o);
    }

    @Override
    public Set<RoutingTableEntry> put(String s, Set<RoutingTableEntry> routingTableEntries) {
        return contentRoutingTable.put(s, routingTableEntries);
    }

    @Override
    public Set<RoutingTableEntry> remove(Object o) {
        return contentRoutingTable.remove(o);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Set<RoutingTableEntry>> map) {
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
    public Collection<Set<RoutingTableEntry>> values() {
        return contentRoutingTable.values();
    }

    @Override
    public Set<Entry<String, Set<RoutingTableEntry>>> entrySet() {
        return contentRoutingTable.entrySet();
    }
}
