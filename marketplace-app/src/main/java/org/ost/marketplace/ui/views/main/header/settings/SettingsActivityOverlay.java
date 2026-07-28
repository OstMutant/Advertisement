package org.ost.marketplace.ui.views.main.header.settings;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.audit.AuditActivityPanel;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.overlay.BaseOverlay;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;

import java.util.List;
import java.util.function.LongConsumer;

import static org.ost.marketplace.services.i18n.I18nKey.HEADER_HOME;
import static org.ost.marketplace.services.i18n.I18nKey.SETTINGS_ACTIVITY_BUTTON;
import static org.ost.marketplace.services.i18n.I18nKey.SETTINGS_SECTION_TITLE;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S2065")
public class SettingsActivityOverlay extends BaseOverlay {

    private final transient EntityOverlaySupport support;
    private final transient I18nService          i18n;
    private final UiComponentFactory<AuditActivityPanel> auditActivityPanelFactory;

    private OverlayBreadcrumbBackButton homeLink;
    private OverlayBreadcrumbBackButton settingsLink;
    private OverlayLayout               layout;
    private Runnable                    onCloseToHome;

    @Override
    protected void buildContent() {
        addClassName("settings-activity-overlay");
        // Breadcrumb chain: Home / Settings / Activity, first two clickable.
        homeLink = support.createBreadcrumbButton(HEADER_HOME, this::closeToHome);
        homeLink.addClassName("settings-activity-breadcrumb-home");
        settingsLink = support.createBreadcrumbButton(SETTINGS_SECTION_TITLE, this::closeToParent);
        settingsLink.addClassName("settings-activity-breadcrumb-settings");
    }

    @Override
    protected void onEsc() {
        closeToParent();
    }

    public void openFor(@NonNull Long userId, boolean isPrivileged, boolean canOperate,
            @NonNull Runnable onCloseToHome, @NonNull LongConsumer onRestoreRequested) {
        ensureInitialized();
        this.onCloseToHome = onCloseToHome;

        if (layout != null) layout.removeFromParent();
        layout = support.createLayout(List.of((Component) homeLink, settingsLink));
        layout.getBreadcrumbCurrent().setText(i18n.get(SETTINGS_ACTIVITY_BUTTON));

        // X closes back to whatever screen opened this overlay (Settings), not necessarily Home.
        UiIconButton closeBtn = new UiIconButton(i18n.get(SETTINGS_SECTION_TITLE), VaadinIcon.CLOSE.create());
        closeBtn.addClassName("settings-activity-close-button");
        closeBtn.addClickListener(_ -> closeToParent());
        layout.setHeaderActions(new Div(closeBtn));

        Div content = new Div(auditActivityPanelFactory.build(AuditActivityPanel.Parameters.builder()
                .entityRef(new EntityRef(EntityType.USER_SETTINGS, userId))
                .userId(userId)
                .isPrivileged(isPrivileged)
                .canOperate(canOperate)
                .onRestoreRequested(snapshotId -> {
                    onRestoreRequested.accept(snapshotId);
                    closeToParent();
                })
                .build()));
        layout.setContent(content);

        add(layout);
        openNested();
    }

    private void closeToParent() {
        closeNested();
    }

    private void closeToHome() {
        closeNested();
        if (onCloseToHome != null) onCloseToHome.run();
    }
}
