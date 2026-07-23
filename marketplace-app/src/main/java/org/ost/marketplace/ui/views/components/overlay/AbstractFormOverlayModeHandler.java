package org.ost.marketplace.ui.views.components.overlay;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import lombok.NonNull;
import lombok.Value;
import org.ost.marketplace.ui.dto.EditDto;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;

import java.util.function.Supplier;

public abstract class AbstractFormOverlayModeHandler<D extends EditDto> implements OverlayModeHandler {

    protected OverlayFormBinder<D> binder;
    protected Div tabbedSecondaryContent;
    protected Tabs formTabs;
    protected Tab editTab;

    @Value
    @lombok.Builder
    public static class ActivityTabParams {
        boolean canOperate;
        boolean isCreateMode;
        @NonNull String editTabLabel;
        @NonNull String activityTabLabel;
        @NonNull String tabsCssClass;
        @NonNull String secondaryContentCssClass;
        @NonNull Div editContent;
        @NonNull ComponentFactory<AuditPort> auditPortFactory;
        @NonNull Supplier<Component> activityContentLoader;
    }

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

    protected Div buildContentWithActivity(ActivityTabParams p) {
        if (p.isCreateMode()) {
            return p.getEditContent();
        }
        return p.getAuditPortFactory().findIfAvailable()
                .filter(_ -> p.isCanOperate())
                .map(_ -> {
                    editTab = new Tab(p.getEditTabLabel());
                    Tab activityTab = new Tab(p.getActivityTabLabel());
                    formTabs = new Tabs(editTab, activityTab);
                    formTabs.addClassName(p.getTabsCssClass());
                    Div result = buildTabbedContent(formTabs, editTab, p.getEditContent(), p.getActivityContentLoader());
                    tabbedSecondaryContent.addClassName(p.getSecondaryContentCssClass());
                    return result;
                })
                .orElse(p.getEditContent());
    }
}
