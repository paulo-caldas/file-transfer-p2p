package Business;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
}
