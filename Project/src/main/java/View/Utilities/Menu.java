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
        clearConsole();
        buildHeader();

        for (Option o : options) {
            String tag = o.getTag();
            String description = o.getDescription();

            int tagLen = tag.length();
            int descriptionLen = description.length();

            System.out.println("| " + description + " " + repeatStringN(".", horizontalSize-descriptionLen-tagLen) + " " + tag + " |");
        }
        System.out.println("+" + repeatStringN("=", horizontalSize) + "+");
        System.out.print("Insert option:");
    }

    private void buildHeader() {
        int titleLen = title.length();

        System.out.println("+" + repeatStringN("=", horizontalSize) + "+");

        System.out.println("|" + repeatStringN(" ", (horizontalSize-titleLen)/2) + title + repeatStringN(" ", (horizontalSize-((horizontalSize-titleLen)/2)-titleLen)) + "|");

        System.out.println("+" + repeatStringN("=", horizontalSize) + "+");
    }
}
