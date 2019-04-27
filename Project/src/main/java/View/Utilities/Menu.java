package View.Utilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static Business.Utils.repeatStringN;

public class Menu {

    private List<Option> options;
    private String title;
    private int horizontalSize;

    /**
     * Take padding into account when drawing header because every
     * menu element is separated by a space on each size
     * if you dont, header and body of menu wont allign
     * [space]text[space]....[space]tag[space]
     * Thus, the padding on each half of the screen is two
     */
    private final int PADDING_SIZE = 4;

    public Menu(String title, List<Option> options) {
        this.options = options;
        this.title = title;

        // Calculate the biggest horizontal string size of the menu, for drawing purposes
        List<String> allDrawableStrings = new ArrayList<>();
        allDrawableStrings.addAll(this.options.stream().map(Option::toString).collect(Collectors.toList()));
        allDrawableStrings.add(this.title);

        this.horizontalSize = allDrawableStrings
                .stream()
                .map(String::length)
                .max(Integer::compareTo)
                .orElse(0);
    }

    private void clearConsole() {
        // ANSI support
        if (System.getenv().get("TERM") != null) {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        } else if (System.getProperty("os.name").contains("Windows")) {
            try {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void show() {
        /**
         * Consider the following menu
         *
         * +==================+  (H1)
         * |     TITLE        |  (H2)
         * +==================+  (H3)
         * | Opt1 ......... 1 |  (O1)
         * | Opt2 ......... 2 |  (O2)
         * | Opt3 ......... 3 |  (O3)
         * |      (...)       |  (ON-1)
         * +==================+  (ON)
         */

        clearConsole();
        buildHeader(); // (H1), (H2), (H3)
        buildOptions(); // from (O1) to (ON)
        System.out.print("Insert option:");
    }

    private void buildOptions() {
        /**
         * Consider the following body
         *
         * | Opt1 ......... 1 | (1)
         * | Opt2 ......... 2 | (2)
         * | Opt3 ......... 3 | (3)
         * |      (...)       | (N-1)
         * +==================+ (N)
         */

        // from (1) to (N-1)
        for (Option o : options) {
            String tag = o.getTag();
            String description = o.getDescription();

            int tagLen = tag.length();
            int descriptionLen = description.length();

            System.out.println("| " + description + " " + repeatStringN(".", horizontalSize-descriptionLen-tagLen) + " " + tag + " |");
        }

        drawLine(); // (N)
    }

    private void buildHeader() {
        int titleLen = title.length();

        /**
         * Consider the following header
         *
         * +================+  (1)
         * |     TITLE      |  (2)
         * +================+  (3)
         */

        // (1)
        drawLine();

        // (2)
        System.out.println("|" + repeatStringN(" ", ((horizontalSize-titleLen)/2) + (PADDING_SIZE/2)) + title + repeatStringN(" ", (horizontalSize-((horizontalSize-titleLen)/2)-titleLen) + (PADDING_SIZE/2)) + "|");

        // (3)
        drawLine();
    }

    private void drawLine() {
        /**
         * +===========+
         */

        System.out.println("+" + repeatStringN("=", horizontalSize + PADDING_SIZE) + "+");
    }
}
