import Business.MobileNetworkNode.MobileNode;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        // Parse arguments
        if (args.length != 1) {
            System.out.println("Usage: java -jar MobileTransfer.jar <directory of content to share>");
            System.exit(0);
        }

        // First argument is the path where the files the user wants to share are
        File sharingDirectory = new File(args[0]).getAbsoluteFile();

        System.out.println("- Starting main");

        try {
            MobileNode node = new MobileNode(sharingDirectory);
            node.autonomousStart();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}