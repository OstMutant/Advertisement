package org.ost.advertisement.ui.views.main.tabs.advertisements.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.audit.dto.AdvertisementHistoryDto;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.core.i18n.I18nService;
import org.ost.advertisement.audit.spi.AuditUiExtension;
import org.ost.advertisement.ui.views.components.buttons.UiIconButton;
import org.ost.advertisement.ui.views.components.buttons.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.advertisement.ui.views.components.overlay.OverlayLayout;
import org.ost.advertisement.ui.views.components.overlay.OverlayModeHandler;
import org.ost.advertisement.attachment.spi.AdvertisementGalleryExtension;
import org.ost.advertisement.ui.views.main.tabs.advertisements.overlay.elements.OverlayAdvertisementMetaPanel;
import org.ost.advertisement.core.ui.Configurable;
import org.ost.advertisement.core.ui.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.ost.advertisement.common.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AdvertisementViewOverlayModeHandler implements OverlayModeHandler,
        Configurable<AdvertisementViewOverlayModeHandler, AdvertisementViewOverlayModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull AdvertisementInfoDto ad;
        @NonNull Runnable             onEdit;
        @NonNull Runnable             onClose;
        @NonNull Consumer<Long>       onRestore;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<AdvertisementViewOverlayModeHandler, Parameters> {
        @Getter
        private final ObjectProvider<AdvertisementViewOverlayModeHandler> provider;
    }

    private final AccessEvaluator               access;
    @Getter
    private final I18nService                   i18nService;
    private final OverlayAdvertisementMetaPanel metaPanel;
    private final UiPrimaryButton               editButton;
    private final UiIconButton                  closeButton;
    private final ObjectProvider<AdvertisementGalleryExtension> galleryExtension;
    private final ObjectProvider<AuditUiExtension>              auditUiExtensionProvider;
    private final ConfirmActionDialog.Builder                   confirmDialogBuilder;

    private Parameters params;

    @Override
    public AdvertisementViewOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    public void activate(OverlayLayout layout) {
        Div viewContent = buildViewContent();

        Tab viewTab = new Tab(getValue(ADVERTISEMENT_VIEW_TAB));
        Tabs tabs = new Tabs(viewTab);
        tabs.addClassName("adv-overlay-tabs");

        Div mainContent;

        AuditUiExtension auditUi = auditUiExtensionProvider.getIfAvailable();
        if (auditUi != null && access.canOperate(params.getAd())) {
            Div historyContent = new Div();
            historyContent.addClassName("adv-history-content");
            historyContent.setVisible(false);

            Tab historyTab = new Tab(getValue(ADVERTISEMENT_HISTORY_TAB));
            tabs.add(historyTab);

            tabs.addSelectedChangeListener(event -> {
                boolean isView = event.getSelectedTab() == viewTab;
                viewContent.setVisible(isView);
                historyContent.setVisible(!isView);
                if (!isView && historyContent.getChildren().findFirst().isEmpty()) {
                    historyContent.add(buildHistoryContent(params.getAd(), auditUi));
                }
            });

            mainContent = new Div(tabs, viewContent, historyContent);
        } else {
            mainContent = new Div(tabs, viewContent);
        }

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

        layout.setContent(mainContent);
        layout.setHeaderActions(new Div(editButton, closeButton));
    }

    private Div buildViewContent() {
        H2 title = new H2(params.getAd().getTitle());
        title.addClassName("overlay__view-title");

        Span description = new Span(params.getAd().getDescription());
        description.addClassName("overlay__view-description");

        Div cardHeader = new Div(VaadinIcon.EYE.create(), new Span(getValue(ADVERTISEMENT_OVERLAY_SECTION_VIEW)));
        cardHeader.addClassName("overlay__view-card-header");

        Div textCard = new Div(cardHeader, title, description);
        textCard.addClassName("overlay__view-card");

        Div viewBody = new Div(textCard);

        galleryExtension.ifAvailable(ext -> viewBody.add(ext.buildGalleryForView(params.getAd().getId())));

        viewBody.add(metaPanel.configure(OverlayAdvertisementMetaPanel.Parameters.from(params.getAd())));
        viewBody.addClassName("overlay__view-body");

        return viewBody;
    }

    private com.vaadin.flow.component.Component buildHistoryContent(AdvertisementInfoDto ad,
                                                                     AuditUiExtension auditUi) {
        return auditUi.buildAdvertisementHistoryPanel(AuditUiExtension.AdvertisementHistoryParams.builder()
                .adId(ad.getId())
                .userId(access.getCurrentUserId())
                .isPrivileged(access.isPrivileged())
                .currentTitle(ad.getTitle())
                .currentDesc(ad.getDescription())
                .canOperate(access.canOperate(ad))
                .onRestoreRequested(this::showRestoreConfirm)
                .labelEmpty(getValue(ADVERTISEMENT_HISTORY_EMPTY))
                .labelCurrentState(getValue(ADVERTISEMENT_HISTORY_CURRENT_STATE))
                .labelRestore(getValue(ADVERTISEMENT_RESTORE_BUTTON))
                .build());
    }

    private void showRestoreConfirm(AdvertisementHistoryDto h, long snapshotId) {
        List<String> lines = new ArrayList<>();

        String noChange = getValue(ADVERTISEMENT_RESTORE_NO_CHANGE);

        if (Objects.equals(params.getAd().getTitle(), h.title())) {
            lines.add(getValue(CHANGES_FIELD_TITLE) + ": " + noChange);
        } else {
            lines.add(getValue(CHANGES_FIELD_TITLE) + ": \"" + truncate(params.getAd().getTitle())
                    + "\" → \"" + truncate(h.title()) + "\"");
        }

        if (Objects.equals(params.getAd().getDescription(), h.description())) {
            lines.add(getValue(CHANGES_FIELD_DESCRIPTION) + ": " + noChange);
        } else {
            lines.add(getValue(CHANGES_FIELD_DESCRIPTION) + ": " + getValue(ADVERTISEMENT_RESTORE_CONFIRM_DESC_CHANGED));
        }

        confirmDialogBuilder.build(
                ConfirmActionDialog.Parameters.builder()
                        .titleKey(ADVERTISEMENT_RESTORE_CONFIRM_TITLE)
                        .message(String.join("\n", lines))
                        .confirmKey(ADVERTISEMENT_RESTORE_CONFIRM_BUTTON)
                        .cancelKey(ADVERTISEMENT_RESTORE_CONFIRM_CANCEL)
                        .onConfirm(() -> params.getOnRestore().accept(snapshotId))
                        .build()
        ).open();
    }

}
