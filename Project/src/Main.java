public class Main {
    public static void main(String[] args) {
        System.out.println("- Starting main");
        MobileNode node = new MobileNode();
        try {
            node.run();
        } catch (InterruptedException e) { }
    }
}