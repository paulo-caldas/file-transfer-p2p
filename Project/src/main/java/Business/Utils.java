package Business;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Utils {

    public static String macByteArrToString(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i],
                    (i < mac.length - 1) ? ":" : ""));
        }

        return sb.toString();
    }

    public static byte[] fileToByteStream(File file) throws IOException {
        byte[] fileInBytes = new byte[(int) file.length()];
        FileInputStream inputStream = new FileInputStream(file);
        inputStream.read(fileInBytes);
        inputStream.close();

        return fileInBytes;
    }

    public static String hashFile(File f, String hashType) throws IOException, NoSuchAlgorithmException {
        byte[] fileInBytes = fileToByteStream(f);
        MessageDigest md = MessageDigest.getInstance(hashType);
        byte[] hashInBytes = md.digest(fileInBytes);

        StringBuilder sb = new StringBuilder();
        for (byte b : hashInBytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    public static List<File> getFilesInsidePath(File initialPath) throws IOException, NoSuchAlgorithmException {
        List<File> files = new ArrayList();
        for (File currPath : initialPath.listFiles()) {
            if (currPath.isFile()) {
                files.add(currPath);
            } else {
                files.addAll(getFilesInsidePath(currPath));
            }
        }

        return files;
    }

    public static String repeatStringN(String str, int n) {
        return String.join("", Collections.nCopies(n, str));
    }

}
