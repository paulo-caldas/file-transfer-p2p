public class MobileNodeKeepaliveDaemon extends Thread{
    private MobileNode representativeNode;

    public MobileNodeKeepaliveDaemon(MobileNode representativeNode) {
        this.representativeNode = representativeNode;
    }

    @Override
    public void run () {
        try {
            while (true) {
                Thread.sleep(5000);
                representativeNode.queryPeers();
                representativeNode.sendHelloMessage(AddressType.LINK_MULTICAST.toString());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
