package View;


import Business.MobileNetworkNode.RoutingInfo.RoutingTableEntry;
import View.Utilities.Menu;
import View.Utilities.Option;

import java.util.ArrayList;
import java.util.List;

/**
 * User view that presents him with all the results found from a query
 * Much like the results page from searching something on a search engine
 */
public class DynamicContentSearchResultsView implements DynamicTextualView<RoutingTableEntry> {
    private Menu menu;
    private int optionIndex;

    public DynamicContentSearchResultsView() {
       this.optionIndex = 0;
       String title = "Search results";
       List<Option> options = new ArrayList<>();
       options.add(new Option("Exit", "E")); // Important option to let the user exit
       this.menu = new Menu(title, options);
    }

    @Override
    public Menu getMenu() {
        return menu;
    }

    @Override
    public String addOption(RoutingTableEntry entry) {
        // An option on this kind of menu is a possible path to a file

        String relatedIndexStr = String.valueOf(optionIndex);
        menu.addOption(optionIndex, new Option(entry.toString(), relatedIndexStr));

        optionIndex++;
        return relatedIndexStr;
    }

    @Override
    public void deleteOption(String optionID) {
        menu.deleteOption(optionID);
    }
}
