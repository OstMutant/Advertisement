package org.ost.marketplace.ui.views.main.tabs.providers.overlay;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.components.overlay.OverlayModeHandler;
import org.ost.marketplace.ui.views.main.header.account.ProviderProfileFormOverlayModeHandler;
import org.ost.marketplace.ui.views.services.OverlayNavigationRegistry;
import org.ost.marketplace.ui.views.utils.BrowserHistoryUtil;
import org.ost.orchestrator.services.ProviderProfileDisplayEnrichmentService;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/**
 * Public catalog overlay for a provider profile -- View and Edit, mirroring
 * {@code AdvertisementOverlay}'s own single-purpose overlay shape (no unrelated Name/Settings
 * chrome). Edit reuses {@code ProviderProfileFormOverlayModeHandler} wholesale, the same form
 * bean {@code AccountOverlay}'s own Provider Profile tab uses -- no duplicated form.
 */
@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class ProviderProfileCatalogOverlay extends AbstractEntityOverlay<ProviderProfileFormOverlayModeHandler> {

    private enum Mode {VIEW, EDIT}

    private record OverlaySession(
            Mode mode,
            ProviderProfileDto profile,
            @NonNull Runnable onListChanged,
            @NonNull Runnable onClosed,
            boolean enteredFromView
    ) {
        OverlaySession toView() { return new OverlaySession(Mode.VIEW, profile, onListChanged, onClosed, false); }
        OverlaySession toEdit() { return new OverlaySession(Mode.EDIT, profile, onListChanged, onClosed, true); }
        OverlaySession withProfile(ProviderProfileDto fresh) { return new OverlaySession(mode, fresh, onListChanged, onClosed, enteredFromView); }
    }

    private static final String LIST_PATH = "";
    private static final String PROVIDER_PATH_PREFIX = "providers/";

    @Getter private final EntityOverlaySupport support;
    private final UiComponentFactory<ProviderProfileCatalogViewModeHandler, ProviderProfileCatalogViewModeHandler.Parameters> viewModeHandlerFactory;
    private final UiComponentFactory<ProviderProfileFormOverlayModeHandler, ProviderProfileFormOverlayModeHandler.Parameters> formModeHandlerFactory;
    private final OverlayNavigationRegistry navigationRegistry;
    private final ProviderProfileSaveService providerProfileSaveService;
    private final ProviderProfileDisplayEnrichmentService enrichmentService;

    private OverlaySession session;

    @PostConstruct
    private void registerHistoryListener() {
        navigationRegistry.register(event -> {
            boolean onProviderPath = event.getLocation().getPath().startsWith(PROVIDER_PATH_PREFIX);
            if (!onProviderPath && session != null && session.mode() == Mode.VIEW && hasClassName("overlay--visible")) {
                session.onClosed().run();
                super.closeToList();
            }
        });
    }

    @Override protected String  getOverlayCssClass()   { return "provider-profile-catalog-overlay"; }
    @Override protected I18nKey getBreadcrumbLabelKey() { return MAIN_TAB_PROVIDERS; }

    @Override protected boolean isEditMode()      { return session.mode() == Mode.EDIT; }
    @Override protected boolean enteredFromView() { return session.enteredFromView(); }

    @Override
    protected SaveConfig saveConfig() {
        return new SaveConfig(
                PROVIDER_PROFILE_OVERLAY_NOTIFICATION_SUCCESS,
                PROVIDER_PROFILE_OVERLAY_NOTIFICATION_VALIDATION_FAILED,
                PROVIDER_PROFILE_OVERLAY_NOTIFICATION_SAVE_ERROR,
                PROVIDER_PROFILE_OVERLAY_NOTIFICATION_CONFLICT);
    }

    @Override
    protected void proceed() {
        applyFreshOrFallback(
                providerProfileSaveService.findById(session.profile().getId()).map(this::enrichSingle),
                fresh -> { session = session.withProfile(fresh).toView(); switchTo(); session.onListChanged().run(); },
                this::closeToList);
    }

    private ProviderProfileDto enrichSingle(ProviderProfileDto profile) {
        return enrichmentService.enrichWithActor(profile);
    }

    @Override
    protected void afterDiscard() {
        if (session.mode() == Mode.EDIT && session.enteredFromView()) {
            session = session.toView();
            switchTo();
        } else {
            closeToList();
        }
    }

    public void openForView(@NonNull ProviderProfileDto profile, @NonNull Runnable onListChanged, @NonNull Runnable onClosed) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.VIEW, profile, onListChanged, onClosed, false));
        BrowserHistoryUtil.pushStateWithBaseSync(PROVIDER_PATH_PREFIX + profile.getId());
    }

    public void openForEdit(@NonNull ProviderProfileDto profile, @NonNull Runnable onListChanged, @NonNull Runnable onClosed) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.EDIT, profile, onListChanged, onClosed, false));
        BrowserHistoryUtil.pushStateWithBaseSync(PROVIDER_PATH_PREFIX + profile.getId());
    }

    private void openSession(OverlaySession s) {
        session = s;
        launchSession(this::switchTo);
    }

    void handleDeleted() {
        session.onListChanged().run();
        closeToList();
    }

    @Override
    protected void switchTo() {
        currentFormHandler = null;
        List<BreadcrumbStep> breadcrumbSteps = buildBreadcrumbSteps();
        layout.setBreadcrumbLinks(buildBreadcrumbLinks(breadcrumbSteps));

        OverlayModeHandler handler = switch (session.mode()) {
            case VIEW -> viewModeHandlerFactory.build(
                    ProviderProfileCatalogViewModeHandler.Parameters.builder()
                            .profile(session.profile())
                            .onEdit(this::switchToEdit)
                            .onDeleted(this::handleDeleted)
                            .onClose(this::closeToList)
                            .build());
            case EDIT -> {
                currentFormHandler = formModeHandlerFactory.build(
                        ProviderProfileFormOverlayModeHandler.Parameters.builder()
                                .targetUserId(session.profile().getActorId())
                                .onSave(this::handleSave)
                                .onCancel(this::handleCancel)
                                .breadcrumbSteps(breadcrumbSteps)
                                .tabBar(new Div())
                                .build());
                yield currentFormHandler;
            }
        };

        handler.activate(layout);

        layout.getBreadcrumbCurrent().setText(switch (session.mode()) {
            case VIEW -> i18n().get(OVERLAY_BREADCRUMB_VIEW);
            case EDIT -> i18n().get(PROVIDER_PROFILE_OVERLAY_SECTION_LABEL);
        });
    }

    private void switchToEdit() {
        session = session.toEdit();
        switchTo();
    }

    @Override
    protected void closeToList() {
        BrowserHistoryUtil.pushStateWithBaseSync(LIST_PATH);
        session.onClosed().run();
        super.closeToList();
    }
}
