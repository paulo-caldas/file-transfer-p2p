package Business.MobileNetworkNode.DataCache;

import java.io.Serializable;

/**
 * Piece of a file. Must have all files to get a file back
 *
 * FIXME: Lots of redundancy, N chunks have the total file size and full file name N times.
 * Altough redundant, is removes the need for auxiliary structures because all the info needed
 * is within the fragment. Nevertheless, a topic to consider in the future
 */
public class FileFragment implements Serializable {

    private byte[] chunk; // A file can be converted into an array of bytes, and vice versa
    private int initbyte; // The byte where this chunk begins
    private int totalSizeBytes; // Total number of bytes in the complete file, useful to see when its the last chunk
    private String fileName; // Name of the full file this chunk refers to

    public FileFragment(byte[] chunk, int initbyte, int totalSizeBytes, String fileName) {
        this.chunk = chunk;
        this.initbyte = initbyte;
        this.totalSizeBytes = totalSizeBytes;
        this.fileName = fileName;
    }

    public byte[] getChunk() {
        return chunk;
    }

    public void setChunk(byte[] chunk) {
        this.chunk = chunk;
    }

    public int getInitbyte() {
        return initbyte;
    }

    public void setInitbyte(int initbyte) {
        this.initbyte = initbyte;
    }

    public int getTotalSizeBytes() {
        return totalSizeBytes;
    }

    public void setTotalSizeBytes(int totalSizeBytes) {
        this.totalSizeBytes = totalSizeBytes;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public String toString() {
        return (this.fileName + ": (" + this.initbyte + "," + this.chunk.length + ") [total= " + this.totalSizeBytes + "]");
    }
}
