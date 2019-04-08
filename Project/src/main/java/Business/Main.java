package Business;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java -jar MobileTransfer.jar <directory of content to share>");
            System.exit(0);
        }
        File sharingDirectory = new File(args[0]).getAbsoluteFile();

        System.out.println("- Starting main");

        try {
            new MobileNode(sharingDirectory).run();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }
}