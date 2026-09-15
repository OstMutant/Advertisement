package org.ost.marketplace.ui.views.main.tabs.advertisements.overlay;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.components.overlay.OverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementViewOverlayModeHandler;
import org.ost.marketplace.ui.views.services.OverlayNavigationRegistry;
import org.ost.marketplace.ui.core.UiComponentFactory;

import java.util.List;
import java.util.function.Consumer;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class AdvertisementOverlay extends AbstractEntityOverlay<AdvertisementFormOverlayModeHandler> {

    private enum Mode {VIEW, EDIT, CREATE}

    private record OverlaySession(
            Mode mode,
            AdvertisementInfoDto ad,
            @NonNull Consumer<AdvertisementInfoDto> onUpdated,
            @NonNull Runnable onListChanged,
            @NonNull Runnable onClosed,
            boolean enteredFromView
    ) {
        OverlaySession toView() { return new OverlaySession(Mode.VIEW, ad, onUpdated, onListChanged, onClosed, false); }
        OverlaySession toEdit() { return new OverlaySession(Mode.EDIT, ad, onUpdated, onListChanged, onClosed, true); }
        OverlaySession withAd(AdvertisementInfoDto fresh) { return new OverlaySession(mode, fresh, onUpdated, onListChanged, onClosed, enteredFromView); }
    }

    private static final String LIST_PATH = "";
    private static final String AD_PATH_PREFIX = "ads/";

    @Getter private final EntityOverlaySupport  support;
    private final UiComponentFactory<AdvertisementViewOverlayModeHandler> viewModeHandlerFactory;
    private final UiComponentFactory<AdvertisementFormOverlayModeHandler> formModeHandlerFactory;
    private final OverlayNavigationRegistry navigationRegistry;

    private OverlaySession session;

    @PostConstruct
    private void registerHistoryListener() {
        navigationRegistry.register(event -> {
            boolean onAdPath = event.getLocation().getPath().startsWith(AD_PATH_PREFIX);
            if (!onAdPath && session != null && session.mode() == Mode.VIEW && hasClassName("overlay--visible")) {
                session.onClosed().run();
                super.closeToList();
            }
        });
    }

    @Override protected String    getOverlayCssClass()    { return "advertisement-overlay"; }
    @Override protected I18nKey   getBreadcrumbLabelKey() { return MAIN_TAB_ADVERTISEMENTS; }

    @Override protected boolean isEditMode()      { return session.mode() == Mode.EDIT; }
    @Override protected boolean enteredFromView() { return session.enteredFromView(); }

    @Override
    protected SaveConfig saveConfig() {
        return new SaveConfig(
                ADVERTISEMENT_OVERLAY_NOTIFICATION_SUCCESS,
                ADVERTISEMENT_OVERLAY_NOTIFICATION_VALIDATION_FAILED,
                ADVERTISEMENT_OVERLAY_NOTIFICATION_SAVE_ERROR,
                ADVERTISEMENT_OVERLAY_NOTIFICATION_CONFLICT);
    }

    @Override
    protected void proceed() {
        if (session.mode() == Mode.EDIT) {
            AdvertisementInfoDto fresh = currentFormHandler.getSavedInfoDto();
            if (fresh != null) {
                session = session.withAd(fresh);
                session.onUpdated().accept(fresh);
            }
        } else {
            session.onListChanged().run();
            closeToList();
        }
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

    public void openForView(AdvertisementInfoDto ad, Consumer<AdvertisementInfoDto> onUpdated, Runnable onClosed) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.VIEW, ad, onUpdated, () -> {}, onClosed, false));
        UI.getCurrent().getPage().getHistory().pushState(null, AD_PATH_PREFIX + ad.getId());
    }

    public void openForCreate(Runnable onListChanged, Runnable onClosed) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.CREATE, null, _ -> {}, onListChanged, onClosed, false));
    }

    public void openForEdit(AdvertisementInfoDto ad, Consumer<AdvertisementInfoDto> onUpdated, Runnable onClosed) {
        ensureInitialized();
        openSession(new OverlaySession(Mode.EDIT, ad, onUpdated, () -> {}, onClosed, false));
    }

    private void openSession(OverlaySession s) {
        session = s;
        launchSession(this::switchTo);
    }

    @Override
    protected void switchTo() {
        currentFormHandler = null;
        List<BreadcrumbStep> breadcrumbSteps = buildBreadcrumbSteps();
        layout.setBreadcrumbLinks(buildBreadcrumbLinks(breadcrumbSteps));
        OverlayModeHandler handler = switch (session.mode()) {
            case VIEW -> viewModeHandlerFactory.build(
                    AdvertisementViewOverlayModeHandler.Parameters.builder()
                            .ad(session.ad())
                            .onEdit(this::switchToEdit)
                            .onClose(this::closeToList)
                            .build());
            case EDIT, CREATE -> {
                currentFormHandler = formModeHandlerFactory.build(
                        AdvertisementFormOverlayModeHandler.Parameters.builder()
                                .ad(session.ad())
                                .onSave(this::handleSave)
                                .onCancel(this::handleCancel)
                                .breadcrumbSteps(breadcrumbSteps)
                                .build());
                yield currentFormHandler;
            }
        };

        handler.activate(layout);

        layout.getBreadcrumbCurrent().setText(switch (session.mode()) {
            case VIEW   -> i18n().get(OVERLAY_BREADCRUMB_VIEW);
            case EDIT   -> i18n().get(ADVERTISEMENT_OVERLAY_TITLE_EDIT);
            case CREATE -> i18n().get(ADVERTISEMENT_OVERLAY_TITLE_NEW);
        });
    }

    private void switchToEdit() {
        if (session.ad() == null) return;
        session = session.toEdit();
        switchTo();
    }

    @Override
    protected void closeToList() {
        UI.getCurrent().getPage().getHistory().pushState(null, LIST_PATH);
        session.onClosed().run();
        super.closeToList();
    }
}
