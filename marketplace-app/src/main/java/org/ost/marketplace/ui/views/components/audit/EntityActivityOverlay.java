package org.ost.marketplace.ui.views.components.audit;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.overlay.BaseOverlay;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.platform.core.model.EntityRef;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S2065")
public class EntityActivityOverlay extends BaseOverlay {

    @lombok.Value
    @lombok.Builder
    public static class Parameters {
        @NonNull EntityRef            entityRef;
        @NonNull Long                 userId;
        boolean                        isPrivileged;
        boolean                        canOperate;
        @NonNull List<BreadcrumbStep> parentSteps;
        @NonNull String                parentFormLabel;
        @NonNull I18nKey              currentLabelKey;
        @NonNull LongConsumer         onRestoreRequested;
    }

    private final transient EntityOverlaySupport support;
    private final transient I18nService          i18n;
    private final UiComponentFactory<AuditActivityPanel> auditActivityPanelFactory;

    private OverlayLayout layout;

    @Override
    protected void buildContent() {
        addClassName("entity-activity-overlay");
    }

    @Override
    protected void onEsc() {
        closeToParent();
    }

    public void openFor(@NonNull Parameters p) {
        ensureInitialized();

        List<Component> links = new ArrayList<>();
        for (BreadcrumbStep step : p.getParentSteps()) {
            OverlayBreadcrumbBackButton stepLink = support.createBreadcrumbButton(step.label(), () -> {
                closeNested();
                step.onClick().run();
            });
            stepLink.addClassName("entity-activity-breadcrumb-step");
            links.add(stepLink);
        }
        OverlayBreadcrumbBackButton parentLink = support.createBreadcrumbButton(p.getParentFormLabel(), this::closeToParent);
        parentLink.addClassName("entity-activity-breadcrumb-parent");
        links.add(parentLink);

        if (layout != null) layout.removeFromParent();
        layout = support.createLayout(links);
        layout.getBreadcrumbCurrent().setText(i18n.get(p.getCurrentLabelKey()));

        UiIconButton closeBtn = new UiIconButton(p.getParentFormLabel(), VaadinIcon.CLOSE.create());
        closeBtn.addClassName("entity-activity-close-button");
        closeBtn.addClickListener(_ -> closeToParent());
        layout.setHeaderActions(new Div(closeBtn));

        Div content = new Div(auditActivityPanelFactory.build(AuditActivityPanel.Parameters.builder()
                .entityRef(p.getEntityRef())
                .userId(p.getUserId())
                .isPrivileged(p.isPrivileged())
                .canOperate(p.isCanOperate())
                .onRestoreRequested(snapshotId -> {
                    p.getOnRestoreRequested().accept(snapshotId);
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
}
