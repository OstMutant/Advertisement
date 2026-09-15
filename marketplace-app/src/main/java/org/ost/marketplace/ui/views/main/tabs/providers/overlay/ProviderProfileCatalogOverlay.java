package org.ost.marketplace.ui.views.main.tabs.providers.overlay;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.overlay.BaseOverlay;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.services.OverlayNavigationRegistry;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.MAIN_TAB_PROVIDERS;
import static org.ost.marketplace.services.i18n.I18nKey.OVERLAY_BREADCRUMB_VIEW;

/**
 * Public catalog overlay for a provider profile -- view-only, unlike {@code AdvertisementOverlay}.
 * Editing a provider profile already has its own dedicated path (AccountOverlay's Provider Profile
 * tab), so this overlay never enters an edit mode and carries no form/save machinery. See
 * {@code marketplace-app/DECISIONS.md} for the ADR recording this design.
 */
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class ProviderProfileCatalogOverlay extends BaseOverlay {

    private record Session(ProviderProfileDto profile, @NonNull Runnable onDeleted, @NonNull Runnable onClosed) {}

    private static final String LIST_PATH = "";
    private static final String PROVIDER_PATH_PREFIX = "providers/";

    @Getter
    private final EntityOverlaySupport support;
    private final UiComponentFactory<ProviderProfileCatalogViewModeHandler> viewModeHandlerFactory;
    private final OverlayNavigationRegistry navigationRegistry;

    private OverlayLayout layout;
    private Session       session;

    @PostConstruct
    private void registerHistoryListener() {
        navigationRegistry.register(event -> {
            boolean onProviderPath = event.getLocation().getPath().startsWith(PROVIDER_PATH_PREFIX);
            if (!onProviderPath && session != null && hasClassName("overlay--visible")) {
                super.closeToList();
                session.onClosed().run();
            }
        });
    }

    @Override
    protected void buildContent() {
        addClassName("provider-profile-catalog-overlay");
    }

    @Override
    protected void onEsc() {
        closeToList();
    }

    public void openForView(@NonNull ProviderProfileDto profile, @NonNull Runnable onDeleted, @NonNull Runnable onClosed) {
        ensureInitialized();
        session = new Session(profile, onDeleted, onClosed);
        switchTo();
        UI.getCurrent().getPage().getHistory().pushState(null, PROVIDER_PATH_PREFIX + profile.getId());
        open();
    }

    void handleDeleted() {
        UI.getCurrent().getPage().getHistory().pushState(null, LIST_PATH);
        super.closeToList();
        session.onDeleted().run();
    }

    private void switchTo() {
        if (layout != null) layout.removeFromParent();
        List<BreadcrumbStep> steps = buildBreadcrumbSteps();
        layout = support.createLayout(List.of());
        layout.setBreadcrumbLinks(buildBreadcrumbLinks(steps));

        ProviderProfileCatalogViewModeHandler handler = viewModeHandlerFactory.build(
                ProviderProfileCatalogViewModeHandler.Parameters.builder()
                        .profile(session.profile())
                        .onDeleted(this::handleDeleted)
                        .onClose(this::closeToList)
                        .build());
        handler.activate(layout);
        layout.getBreadcrumbCurrent().setText(support.getI18n().get(OVERLAY_BREADCRUMB_VIEW));

        add(layout);
    }

    private List<BreadcrumbStep> buildBreadcrumbSteps() {
        return List.of(new BreadcrumbStep(support.getI18n().get(MAIN_TAB_PROVIDERS), this::closeToList));
    }

    private List<Component> buildBreadcrumbLinks(List<BreadcrumbStep> steps) {
        return steps.stream()
                .<Component>map(step -> support.createBreadcrumbButton(step.label(), step.onClick()))
                .toList();
    }

    @Override
    protected void closeToList() {
        UI.getCurrent().getPage().getHistory().pushState(null, LIST_PATH);
        super.closeToList();
        session.onClosed().run();
    }
}
