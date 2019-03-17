public enum HardwareAddressType {
    BROADCAST("FF:FF:FF:FF:FF:FF"),
    MULTICAST("FF:FF:FF:FF:FF:FF")
    ;

    private final String text;

    /**
     * @param text
     */
    HardwareAddressType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
