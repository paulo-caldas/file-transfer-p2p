package View.Utilities;

/**
 * An option in a textual menu
 */
public class Option {
    String descrition;
    String tag; // The tag is the string the user must type to select this options

    public Option(String descrition, String tag) {
        this.descrition = descrition;
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }

    public String getDescription() {
        return descrition;
    }

    public String toString () {
        return descrition + " ... " + tag;
    }
}
