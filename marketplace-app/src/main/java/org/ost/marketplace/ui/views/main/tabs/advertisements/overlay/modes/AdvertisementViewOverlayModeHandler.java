package org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.marketplace.security.AccessEvaluator;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.audit.spi.AuditUiPort;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.components.overlay.AbstractViewOverlayModeHandler;
import org.ost.platform.attachment.spi.AttachmentGalleryPort;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.elements.OverlayAdvertisementMetaPanel;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.ui.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.function.Consumer;

import static org.ost.marketplace.common.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AdvertisementViewOverlayModeHandler extends AbstractViewOverlayModeHandler
        implements Configurable<AdvertisementViewOverlayModeHandler, AdvertisementViewOverlayModeHandler.Parameters>,
                   I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull AdvertisementInfoDto ad;
        @NonNull Runnable             onEdit;
        @NonNull Runnable             onClose;
        @NonNull Consumer<Long>       onRestore;
    }

    private final AccessEvaluator                                   access;
    @Getter
    private final I18nService                                       i18nService;
    private final OverlayAdvertisementMetaPanel                     metaPanel;
    private final UiPrimaryButton                                   editButton;
    private final UiIconButton                                      closeButton;
    private final transient ComponentFactory<AttachmentGalleryPort> galleryPortFactory;
    private final transient ComponentFactory<AuditUiPort>           auditUiPortFactory;
    private final transient ComponentFactory<ConfirmActionDialog>   confirmDialogFactory;

    private Parameters params;

    @Override
    public AdvertisementViewOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    protected String tabsCssClass() {
        return "adv-overlay-tabs";
    }

    @Override
    protected Tab buildPrimaryTab() {
        return new Tab(getValue(ADVERTISEMENT_VIEW_TAB));
    }

    @Override
    protected Div buildPrimaryContent() {
        H2 title = new H2(params.getAd().getTitle());
        title.addClassName("overlay__view-title");

        Span description = new Span(params.getAd().getDescription());
        description.addClassName("overlay__view-description");

        Div cardHeader = new Div(VaadinIcon.EYE.create(), new Span(getValue(ADVERTISEMENT_OVERLAY_SECTION_VIEW)));
        cardHeader.addClassName("overlay__view-card-header");

        Div textCard = new Div(cardHeader, title, description);
        textCard.addClassName("overlay__view-card");

        Div viewBody = new Div(textCard);
        galleryPortFactory.ifAvailable(ext -> viewBody.add(
                ext.buildGalleryForView(new EntityRef(EntityType.ADVERTISEMENT, params.getAd().getId()))));
        viewBody.add(metaPanel.configure(OverlayAdvertisementMetaPanel.Parameters.from(params.getAd())));
        viewBody.addClassName("overlay__view-body");

        return viewBody;
    }

    @Override
    protected SecondaryTabDef buildSecondaryTab() {
        return auditUiPortFactory.findIfAvailable()
                .filter(_ -> access.canOperate(params.getAd()))
                .map(auditUi -> new SecondaryTabDef(
                        new Tab(getValue(ADVERTISEMENT_HISTORY_TAB)),
                        "entity-history-content",
                        () -> buildHistoryContent(params.getAd(), auditUi)))
                .orElse(null);
    }

    @Override
    protected Div buildHeaderActions() {
        editButton.configure(UiPrimaryButton.Parameters.builder()
                .labelKey(ADVERTISEMENT_CARD_BUTTON_EDIT)
                .build());
        closeButton.configure(UiIconButton.Parameters.builder()
                .labelKey(MAIN_TAB_ADVERTISEMENTS)
                .icon(VaadinIcon.CLOSE.create())
                .build());
        editButton.addClickListener(_  -> params.getOnEdit().run());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.canOperate(params.getAd()));
        return new Div(editButton, closeButton);
    }

    private com.vaadin.flow.component.Component buildHistoryContent(AdvertisementInfoDto ad, AuditUiPort auditUi) {
        return auditUi.buildAuditHistoryPanel(AuditUiPort.EntityHistoryParams.builder()
                .entityType(EntityType.ADVERTISEMENT)
                .entityId(ad.getId())
                .userId(access.getCurrentUserId())
                .isPrivileged(access.isPrivileged())
                .canOperate(access.canOperate(ad))
                .onRestoreRequested(this::showRestoreConfirm)
                .build());
    }

    private void showRestoreConfirm(AuditHistoryItemDto h, long snapshotId) {
        confirmDialogFactory.build(
                ConfirmActionDialog.Parameters.builder()
                        .titleKey(ADVERTISEMENT_RESTORE_CONFIRM_TITLE)
                        .message(getValue(ADVERTISEMENT_RESTORE_CONFIRM_DESC_CHANGED))
                        .confirmKey(ADVERTISEMENT_RESTORE_CONFIRM_BUTTON)
                        .cancelKey(ADVERTISEMENT_RESTORE_CONFIRM_CANCEL)
                        .onConfirm(() -> params.getOnRestore().accept(snapshotId))
                        .build()
        ).open();
    }
}
