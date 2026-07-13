package org.ost.marketplace.ui.views.components.overlay;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import org.ost.marketplace.ui.dto.EditDto;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;

import java.util.function.Supplier;

public abstract class AbstractFormOverlayModeHandler<D extends EditDto> implements OverlayModeHandler {

    protected OverlayFormBinder<D> binder;
    protected Div tabbedSecondaryContent;

    public boolean hasChanges() {
        return binder != null && binder.hasChanges();
    }

    public abstract boolean save();
    public abstract void afterSave(boolean success);
    public abstract void discardChanges();

    protected static void wireSaveGuard(UiPrimaryButton saveBtn, Runnable onSave) {
        saveBtn.addClickListener(_ -> {
            saveBtn.setEnabled(false);
            onSave.run();
        });
    }

    protected Div buildTabbedContent(Tabs tabs, Tab primaryTab, Div primaryContent, Supplier<Component> secondaryLoader) {
        tabbedSecondaryContent = new Div();
        tabbedSecondaryContent.setVisible(false);

        tabs.addSelectedChangeListener(event -> {
            boolean isPrimary = event.getSelectedTab() == primaryTab;
            primaryContent.setVisible(isPrimary);
            tabbedSecondaryContent.setVisible(!isPrimary);
            if (!isPrimary && tabbedSecondaryContent.getChildren().findFirst().isEmpty()) {
                tabbedSecondaryContent.add(secondaryLoader.get());
            }
        });

        return new Div(tabs, primaryContent, tabbedSecondaryContent);
    }
}
