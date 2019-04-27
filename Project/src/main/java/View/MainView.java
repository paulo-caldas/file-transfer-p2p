package View;

import View.Utilities.Menu;
import View.Utilities.Option;

import java.util.Arrays;

public class MainView implements TextualView {

    private final Menu menu =
            new Menu("Main menu",
                    Arrays.asList(
                            new Option("Download file", "D"),
                            new Option("Exit", "E")
                    ));

    public Menu getMenu() {
        return menu;
    }
}
