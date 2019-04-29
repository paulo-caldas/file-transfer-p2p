package View;


import Business.MobileNetworkNode.RoutingInfo.RoutingTableEntry;
import View.Utilities.Menu;
import View.Utilities.Option;

import java.util.ArrayList;
import java.util.List;

public class DynamicContentSearchResultsView implements DynamicTextualView<RoutingTableEntry> {
    private Menu menu;
    private int optionIndex;

    public DynamicContentSearchResultsView() {
       this.optionIndex = 0;
       List<Option> options = new ArrayList<>();
       options.add(new Option("Exit", "E"));
       this.menu = new Menu("Search results", options);
    }

    @Override
    public Menu getMenu() {
        return menu;
    }

    @Override
    public String addOption(RoutingTableEntry entry) {
        String relatedIndexStr = String.valueOf(optionIndex);
        String optionDescr = String.format("%s (%d hop(s) away)", entry.getFileName(), entry.getHopCount());
        menu.addOption(optionIndex, new Option(optionDescr, relatedIndexStr));

        optionIndex++;
        return relatedIndexStr;
    }

    @Override
    public void deleteOption(String optionID) {
        menu.deleteOption(optionID);
    }
}
