package Business;

import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Static utilities that aren't inherent to a class
 */
public class Utils {

    /**
     * Format a byte array into the standard, human-readable, MAC address
     *
     * @param mac Mac address as a byte array
     * @return
     */
    public static String macByteArrToString(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i],
                    (i < mac.length - 1) ? ":" : ""));
        }

        return sb.toString();
    }

    /**
     * Format a file into a byte array. Helpful to serialize objects
     *
     * @param file File to turn into byte array
     * @return File as a byte array
     * @throws IOException Whilst reading file
     */
    public static byte[] fileToByteStream(File file) throws IOException {
        byte[] fileInBytes = new byte[(int) file.length()];
        FileInputStream inputStream = new FileInputStream(file);
        inputStream.read(fileInBytes);
        inputStream.close();

        return fileInBytes;
    }

    /**
     * Hash a file
     *
     * @param file     File to hash
     * @param hashType Hashing algorithm to use
     * @return Hash of file
     * @throws IOException              When turning file to byte array
     * @throws NoSuchAlgorithmException When choosing hash passed as argument
     */
    public static String hashFile(File file, String hashType) throws IOException, NoSuchAlgorithmException {
        byte[] fileInBytes = fileToByteStream(file);
        MessageDigest md = MessageDigest.getInstance(hashType);
        byte[] hashInBytes = md.digest(fileInBytes);

        StringBuilder sb = new StringBuilder();
        for (byte b : hashInBytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    /**
     * Retrieve all the files in a path applying recursion
     * The param may be of type File, but it must be a diretory!
     *
     * @param initialPath Initial directory to start from
     * @return List of files inside that directory and all it's subdirectories
     */
    public static List<File> getFilesInsidePath(File initialPath) {
        List<File> files = new ArrayList<>();
        for (File currPath : initialPath.listFiles()) {
            if (currPath.isFile()) {
                files.add(currPath);
            } else {
                files.addAll(getFilesInsidePath(currPath));
            }
        }

        return files;
    }

    /**
     * Repeat a string N times
     *
     * @param str String to repeat
     * @param n   Number of times to repeat
     * @return String concatenated into itself, N times
     */
    public static String repeatStringN(String str, int n) {
        return String.join("", Collections.nCopies(n, str));
    }

    /**
     * Get in string format a timetamp of milliseconds that have passed since unix time
     *
     * @return Timestamp of the moment the method was called
     */
    public static String getTimestampOfNow() {
        return new Timestamp(Calendar.getInstance().getTime().getTime()).toString();
    }
}
