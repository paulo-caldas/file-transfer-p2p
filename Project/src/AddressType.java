public enum AddressType {
    LINK_BROADCAST("FF:FF:FF:FF:FF:FF"),
    LINK_MULTICAST("FF:FF:FF:FF:FF:FF"),
    NETWORK_BROADCAST("255.255.255.255"),
    NETWORK_MULTICAST("255.255.255.255"),
    LISTENING_PORT("6789");

    private final String text;

    /**
     * @param text
     */
    AddressType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
