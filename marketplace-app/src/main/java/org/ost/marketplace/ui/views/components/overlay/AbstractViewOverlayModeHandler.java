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

        TertiaryTabDef tertiary = buildTertiaryTab();
        if (tertiary != null) tabs.add(tertiary.tab());

        layout.setContent(assembleTabbedContent(tabs, primaryTab, buildPrimaryContent(), secondary, tertiary));
        layout.setHeaderActions(buildHeaderActions());
    }

    protected abstract String tabsCssClass();

    protected abstract Tab buildPrimaryTab();

    protected abstract Div buildPrimaryContent();

    protected abstract SecondaryTabDef buildSecondaryTab();

    protected TertiaryTabDef buildTertiaryTab() { return null; }

    protected abstract Div buildHeaderActions();

    public record SecondaryTabDef(Tab tab, String cssClass, Supplier<Component> loader) {}

    public record TertiaryTabDef(Tab tab, String cssClass, Supplier<Component> loader) {}

    private static Div assembleTabbedContent(Tabs tabs, Tab primaryTab, Div primaryContent,
                                             SecondaryTabDef secondary, TertiaryTabDef tertiary) {
        if (secondary == null) {
            return new Div(tabs, primaryContent);
        }
        Div secondaryContent = new Div();
        secondaryContent.addClassName(secondary.cssClass());
        secondaryContent.setVisible(false);

        Div tertiaryContent;
        if (tertiary != null) {
            tertiaryContent = new Div();
            tertiaryContent.addClassName(tertiary.cssClass());
            tertiaryContent.setVisible(false);
        } else {
            tertiaryContent = null;
        }

        tabs.addSelectedChangeListener(event -> {
            Tab selected = event.getSelectedTab();
            boolean isPrimary   = selected == primaryTab;
            boolean isSecondary = selected == secondary.tab();
            primaryContent.setVisible(isPrimary);
            secondaryContent.setVisible(isSecondary);
            if (isSecondary && secondaryContent.getChildren().findFirst().isEmpty()) {
                secondaryContent.add(secondary.loader().get());
            }
            if (tertiaryContent != null) {
                boolean isTertiary = !isPrimary && !isSecondary;
                tertiaryContent.setVisible(isTertiary);
                if (isTertiary && tertiaryContent.getChildren().findFirst().isEmpty()) {
                    tertiaryContent.add(tertiary.loader().get());
                }
            }
        });

        Div wrapper = tertiaryContent != null
                ? new Div(tabs, primaryContent, secondaryContent, tertiaryContent)
                : new Div(tabs, primaryContent, secondaryContent);
        return wrapper;
    }
}
