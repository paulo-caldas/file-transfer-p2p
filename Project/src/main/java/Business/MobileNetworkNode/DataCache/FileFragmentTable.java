package Business.MobileNetworkNode.DataCache;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Map files into a list of fragments, thus making it possible to split a file and retrieve it as a whole
 */
public class FileFragmentTable<A> {

    // Why LinkedList instead of a byte array? If the later is chosen, we'd need to have maybe gigabytes of an array pre-allocated
    // and taking up ram space until all chunks get written. With a link list, the structure slowly grows and takes as much
    // space as it needs to. The choice is thus space versus speed, but do consider that the linked alternative is fast enough (nextByte = (initByteOfFragment + sizeOfFragment)) per interation
    private Map<A, LinkedList<FileFragment>> fileFragmentsMap;

    public FileFragmentTable() {
        this.fileFragmentsMap = new HashMap<>();
    }

    /**
     * Splits a file and save its chunks
     * @param file File to split
     */
    public void insertFile(A key, File file, int chunkSize) throws IOException {
        LinkedList<FileFragment> fileFragments = splitIntoFragments(file, chunkSize);
        fileFragmentsMap.put(key, fileFragments);
    }

    /**
     * Save a file fragment
     * @param key Key that the file's fragments are to be mapped into
     * @param fragmentToAdd Fragment to include in the list of fragments regarding a file
     * @throws IOException When trying to write to file
     * @return 1 if all the files were gathered into a single file, False if more are still missing
     *         0 if its a new chunk
     *         -1 if its a repeated chunk
     */
    public int putFragment(A key, FileFragment fragmentToAdd) throws IOException {
        LinkedList<FileFragment> existingFragments;

        if (!fileFragmentsMap.containsKey(key)) {
            existingFragments = new LinkedList<>();
            existingFragments.addLast(fragmentToAdd);
        } else {
            existingFragments = fileFragmentsMap.get(key);

            int initByteOfFragment = fragmentToAdd.getInitbyte();

            int i = 0;

            while ((i < existingFragments.size()) && (existingFragments.get(i).getInitbyte() - initByteOfFragment <= 0)) {
                if (existingFragments.get(i).getInitbyte() == initByteOfFragment) {
                    // We already have this chunk
                    return -1;
                }
                i++;
            }

            existingFragments.add(i, fragmentToAdd);
        }

        // Update table with the newly added fragment to the list of fragments associated with that filehash
        fileFragmentsMap.put(key, existingFragments);

        // Only save to file if the now new fragment list constitutes a new file
        if (isFragmentsCompleteFile(existingFragments)) {
            // Save all the fragments into a usable file (thus download complete)
            turnChunksToFile(key);

            // Since the job is done, remove the chunks from memory
            fileFragmentsMap.remove(key);

            return 1;
        } else {
            return 0;
        }

    }

    public FileFragment getFragment(A key, int requestedInitByte) {
        LinkedList<FileFragment> fragments = fileFragmentsMap.get(key);

        if (fragments == null) {
            return null;
        }

        for (FileFragment fragment : fragments) {
            int byteFragmentStartsAt = fragment.getInitbyte();
            if (byteFragmentStartsAt == requestedInitByte) {
                return fragment;

                // We can do this only because the linked list is ordered by initial byte of fragment!!
            } else if (byteFragmentStartsAt > requestedInitByte) {
                // We know we won't find it in the rest of the list
                return null;
            }

        }

        // Didnt find it...
        throw null;
    }

    public boolean hasFragment(A key, int requestedInitByte) {
        LinkedList<FileFragment> fragments = fileFragmentsMap.get(key);

        if (fragments == null) {
            return false;
        }

        for (FileFragment fragment : fragments) {
            int byteFragmentStartsAt = fragment.getInitbyte();
            if (byteFragmentStartsAt == requestedInitByte) {
                return true;

                // We can do this only because the linked list is ordered by initial byte of fragment!!
            } else if (byteFragmentStartsAt > requestedInitByte) {
                // We know we won't find it in the rest of the list
                return false;
            }

        }

        // Didnt find it...
        return false;
    }

    private boolean isFragmentsCompleteFile(LinkedList<FileFragment> fileFragments) {
        // Start by the assumption that the file IS complete, and look for conditions
        // where this would not be true
        boolean isFileComplete = true;
        int currByte = 0;

        // This variable is repeated in every fragment, so we retrieve from the first fragment for no
        // particular reason (The redundancy makes it easier for the receiver to know on a per packet
        // basis how far he is from finishing)
        int fullFileSize = fileFragments.getFirst().getTotalSizeBytes();

        // Reminder that fileFragments linkedList is sorted by initByte
        for (FileFragment fragment : fileFragments) {
            int initByteOfFragment = fragment.getInitbyte();

            if (currByte != initByteOfFragment) {
                isFileComplete = false;
                break;
            } else {
                // Fragments are in synch so far
                int sizeOfFragment = fragment.getChunk().length;

                currByte += sizeOfFragment;
            }
        }

        // When the looped ended, currByte is the last byte of the file
        // If the chunks are indeed all there, then this variable should
        // be the size of the file
        if (currByte != fullFileSize) {
            isFileComplete = false;
        }

        return isFileComplete;
    }

    public int getNextNonExistingInitByte(A key) {
        LinkedList<FileFragment> sortedFragments = fileFragmentsMap.get(key);

        if (sortedFragments == null) {
            // We have not a single fragment, aka you should ask for byte 0 next

            return 0;
        }

        // the fragments are ordered by initByte order, thus the next one follows the last fragment
        FileFragment lastFragment = sortedFragments.getLast();

        int initByteLastChunk = lastFragment.getInitbyte();
        int lengthOfLastChunk = lastFragment.getChunk().length;

        return initByteLastChunk + lengthOfLastChunk;
    }

    private void turnChunksToFile(A key) throws IOException {
        List<FileFragment> sortedFragments = fileFragmentsMap.get(key);

        FileFragment anyFragment = sortedFragments.get(0);

        int fullFileSize = anyFragment.getTotalSizeBytes();
        String fullFileName = anyFragment.getFileName();

        byte[] fullBytes = new byte[fullFileSize];

        sortedFragments.forEach(
                fragment ->  {
                    byte[] dataChunk = fragment.getChunk();
                    System.arraycopy(dataChunk,
                            0,
                            fullBytes,
                            fragment.getInitbyte(),
                            dataChunk.length);
                }
        );

        File file = new File("transfered_" + fullFileName);

        try {
            FileUtils.writeByteArrayToFile(file, fullBytes);
        } catch (IOException e) {
            // Something went horribly wrong, delete everything and abort
            fileFragmentsMap.remove(key);

            throw new IOException();
        }
    }

    /**
     * Split a file into a list of correspondant fragments, making it possible to rebuilid
     *
     * @param file      File to split
     * @param chunkSize Max size of each chunk (try to use it as long as its possible)
     * @return List of chunks, ordered by initialByte of chunk (although order is not needed since each fragment has the initByte variable, so you can reconstruct either way)
     * @throws IOException On reading inputstream
     */
    public static LinkedList<FileFragment> splitIntoFragments(File file, int chunkSize) throws IOException {
        LinkedList<FileFragment> fragments = new LinkedList<>();

        FileInputStream inputStream = null;
        int totalSize = (int) file.length();
        String fileName = file.getName();


        byte[] byteStream = new byte[totalSize];

        inputStream = new FileInputStream(file);
        inputStream.read(byteStream);
        inputStream.close();

        int sizeLeft = totalSize;

        int index = 0;
        while (sizeLeft > 0) {
            int currChunkSize;

            if (sizeLeft >= chunkSize) {
                currChunkSize = chunkSize;
                sizeLeft -= chunkSize;
            } else {
                currChunkSize = sizeLeft;
                sizeLeft = 0;
            }

            byte[] chunk = Arrays.copyOfRange(byteStream, index * chunkSize, (index * chunkSize) + currChunkSize);

            FileFragment fragment = new FileFragment(chunk, index * chunkSize, totalSize, fileName);
            fragments.addLast(fragment);
            index++;
        }

        return fragments;
    }

}
