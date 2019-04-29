package View;

import View.Utilities.Menu;

public interface DynamicTextualView<B> {
    Menu getMenu();
    String addOption(B optionDescription); // Returns option id
    void deleteOption(String optionID);
}
