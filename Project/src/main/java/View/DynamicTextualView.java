package View;

import View.Utilities.Menu;

/**
 * Interface of a view that can dynamically change its options
 * @param <B> Type of option
 */
public interface DynamicTextualView<B> {
    Menu getMenu();
    String addOption(B optionDescription); // Returns option id
    void deleteOption(String optionID);
}
