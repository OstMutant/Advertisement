package org.ost.marketplace.ui.views.components.overlay;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;

import java.util.function.Supplier;

public abstract class AbstractViewOverlayModeHandler implements OverlayModeHandler {

    @Override
    public final void activate(OverlayLayout layout) {
        Tab primaryTab = buildPrimaryTab();
        Tabs tabs = new Tabs(primaryTab);
        tabs.addClassName(tabsCssClass());

        SecondaryTabDef secondary = buildSecondaryTab();
        if (secondary != null) tabs.add(secondary.tab());

        layout.setContent(assembleTabbedContent(tabs, primaryTab, buildPrimaryContent(), secondary));
        layout.setHeaderActions(buildHeaderActions());
    }

    protected abstract String tabsCssClass();

    protected abstract Tab buildPrimaryTab();

    protected abstract Div buildPrimaryContent();

    protected abstract SecondaryTabDef buildSecondaryTab();

    protected abstract Div buildHeaderActions();

    public record SecondaryTabDef(Tab tab, String cssClass, Supplier<Component> loader) {}

    private static Div assembleTabbedContent(Tabs tabs, Tab primaryTab, Div primaryContent,
                                             SecondaryTabDef secondary) {
        if (secondary == null) {
            return new Div(tabs, primaryContent);
        }
        Div secondaryContent = new Div();
        secondaryContent.addClassName(secondary.cssClass());
        secondaryContent.setVisible(false);
        tabs.addSelectedChangeListener(event -> {
            boolean isPrimary = event.getSelectedTab() == primaryTab;
            primaryContent.setVisible(isPrimary);
            secondaryContent.setVisible(!isPrimary);
            if (!isPrimary && secondaryContent.getChildren().findFirst().isEmpty()) {
                secondaryContent.add(secondary.loader().get());
            }
        });
        return new Div(tabs, primaryContent, secondaryContent);
    }
}
