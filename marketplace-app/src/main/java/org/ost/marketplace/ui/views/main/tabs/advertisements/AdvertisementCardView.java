package org.ost.marketplace.ui.views.main.tabs.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.platform.core.ComponentFactory;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.views.main.tabs.advertisements.card.AdvertisementCardMetaPanel;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.AdvertisementOverlay;
import org.ost.marketplace.ui.views.components.buttons.action.DeleteActionButton;
import org.ost.marketplace.ui.views.components.buttons.action.EditActionButton;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;

import org.ost.platform.attachment.model.AttachmentMediaContentType;
import org.ost.marketplace.ui.views.components.attachment.AttachmentGalleryService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementCardView extends HorizontalLayout
        implements Configurable<AdvertisementCardView, AdvertisementCardView.Parameters>, I18nParams, Initialization<AdvertisementCardView> {

    private static final String CLICK_EVENT      = "click";
    private static final String STOP_PROPAGATION = "event.stopPropagation()";

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull AdvertisementInfoDto ad;
        @NonNull Runnable             onChanged;
    }

    @Getter
    private final transient I18nService                               i18nService;
    private final transient NotificationService                       notificationService;
    private final transient ComponentFactory<AdvertisementPort>         advertisementPortFactory;
    private final transient UiComponentFactory<AttachmentGalleryService> galleryServiceFactory;
    private final transient UiComponentFactory<AdvertisementCardMetaPanel> metaPanelFactory;
    private final transient UiComponentFactory<EditActionButton>         editButtonFactory;
    private final transient UiComponentFactory<DeleteActionButton>       deleteButtonFactory;
    private final transient UiComponentFactory<ConfirmActionDialog>      confirmDialogFactory;
    private final transient AccessEvaluator                            access;
    private final transient AdvertisementOverlay                       overlay;

    @Override
    @PostConstruct
    public AdvertisementCardView init() {
        addClassName("advertisement-card");
        return this;
    }

    @Override
    public AdvertisementCardView configure(Parameters p) {
        AdvertisementInfoDto ad        = p.getAd();
        Runnable             onChanged = p.getOnChanged();

        getElement().addEventListener(CLICK_EVENT, _ -> overlay.openForView(ad, onChanged));
        getElement().setAttribute("tabindex", "0");
        getElement().addEventListener("keydown", _ -> overlay.openForView(ad, onChanged))
                .setFilter("event.key === 'Enter' || event.key === ' '");

        Div thumbnail = createThumbnail(ad);
        if (thumbnail != null) add(thumbnail);
        add(createContent(ad, onChanged));

        return this;
    }

    private Div createThumbnail(AdvertisementInfoDto ad) {
        if (ad.getMediaUrl() == null) {
            return null;
        }
        Div wrapper = new Div();
        wrapper.addClassName("advertisement-thumbnail-wrapper");

        if (AttachmentMediaContentType.isUploadedVideo(ad.getMediaContentType())) {
            com.vaadin.flow.dom.Element video = new com.vaadin.flow.dom.Element("video");
            video.setAttribute("src", ad.getMediaUrl());
            video.setAttribute("preload", "metadata");
            video.setAttribute("muted", "");
            video.getClassList().add("advertisement-thumbnail");
            wrapper.getElement().appendChild(video);
            Icon playIcon = VaadinIcon.PLAY_CIRCLE_O.create();
            playIcon.addClassName("advertisement-thumbnail-play");
            wrapper.add(playIcon);
        } else {
            Image img = new Image(ad.getMediaUrl(), ad.getTitle());
            img.addClassName("advertisement-thumbnail");
            wrapper.add(img);
        }

        if (ad.getMediaCount() != null && ad.getMediaCount() > 1) {
            Span badge = new Span(VaadinIcon.CAMERA.create(), new Span(String.valueOf(ad.getMediaCount())));
            badge.addClassName("advertisement-thumbnail-badge");
            wrapper.add(badge);
        }
        wrapper.getElement().addEventListener(CLICK_EVENT, _ ->
                galleryServiceFactory.ifAvailable(ext -> ext.openMediaLightbox(new EntityRef(EntityType.ADVERTISEMENT, ad.getId())))
        ).addEventData(STOP_PROPAGATION);
        return wrapper;
    }

    private VerticalLayout createContent(AdvertisementInfoDto ad, Runnable onChanged) {
        Span spacer = new Span();

        HorizontalLayout bottom = new HorizontalLayout(
                createMetaPanel(ad),
                createActions(ad, onChanged)
        );
        bottom.setWidthFull();
        bottom.setAlignItems(Alignment.END);
        bottom.setJustifyContentMode(JustifyContentMode.BETWEEN);

        VerticalLayout content = new VerticalLayout(
                createTitle(ad),
                createDescription(ad),
                spacer,
                bottom
        );
        content.addClassName("advertisement-content");
        content.setPadding(false);
        content.setSpacing(false);
        content.setFlexGrow(1, spacer);
        return content;
    }

    private H3 createTitle(AdvertisementInfoDto ad) {
        H3 title = new H3(ad.getTitle());
        title.addClassName("advertisement-title");
        return title;
    }

    private Span createDescription(AdvertisementInfoDto ad) {
        Span description = new Span(ad.getDescription());
        description.addClassName("advertisement-description");
        return description;
    }

    private AdvertisementCardMetaPanel createMetaPanel(AdvertisementInfoDto ad) {
        boolean neverEdited = ad.getUpdatedAt() == null
                || ad.getUpdatedAt().equals(ad.getCreatedAt());

        return metaPanelFactory.build(AdvertisementCardMetaPanel.Parameters.builder()
                .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                .authorEmail(ad.getCreatedByUserEmail())
                .dateLabel(neverEdited
                        ? getValue(ADVERTISEMENT_CARD_CREATED)
                        : getValue(ADVERTISEMENT_CARD_UPDATED))
                .date(neverEdited ? ad.getCreatedAt() : ad.getUpdatedAt())
                .build());
    }

    private HorizontalLayout createActions(AdvertisementInfoDto ad, Runnable onChanged) {
        boolean canOperate = access.canOperate(ad.getOwnerUserId());

        Button edit   = createEditButton(ad, onChanged, canOperate);
        Button delete = createDeleteButton(ad, onChanged, canOperate);

        HorizontalLayout actions = new HorizontalLayout(edit, delete);
        actions.addClassName("advertisement-actions");
        return actions;
    }

    private Button createEditButton(AdvertisementInfoDto ad, Runnable onChanged, boolean visible) {
        Button edit = editButtonFactory.build(
                EditActionButton.Parameters.builder()
                        .tooltip(getValue(ADVERTISEMENT_CARD_BUTTON_EDIT))
                        .onClick(() -> overlay.openForEdit(ad, onChanged))
                        .small(true)
                        .cssClassName("advertisement-edit")
                        .build()
        );
        edit.setVisible(visible);
        edit.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
        return edit;
    }

    private Button createDeleteButton(AdvertisementInfoDto ad, Runnable onChanged, boolean visible) {
        Button delete = deleteButtonFactory.build(
                DeleteActionButton.Parameters.builder()
                        .tooltip(getValue(ADVERTISEMENT_CARD_BUTTON_DELETE))
                        .onClick(() -> confirmAndDelete(ad, onChanged))
                        .small(true)
                        .cssClassName("advertisement-delete")
                        .build()
        );
        delete.setVisible(visible);
        delete.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData(STOP_PROPAGATION);
        return delete;
    }

    private void confirmAndDelete(AdvertisementInfoDto ad, Runnable onChanged) {
        confirmDialogFactory.build(
                ConfirmActionDialog.Parameters.builder()
                        .titleKey(ADVERTISEMENT_VIEW_CONFIRM_DELETE_TITLE)
                        .message(getValue(ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT, ad.getTitle(), ad.getId()))
                        .confirmKey(ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON)
                        .cancelKey(ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON)
                        .onConfirm(() -> {
                            try {
                                advertisementPortFactory.ifAvailable(p -> p.delete(ad.getId(), access.getCurrentUserId()));
                                notificationService.success(ADVERTISEMENT_VIEW_NOTIFICATION_DELETED);
                                onChanged.run();
                            } catch (Exception _) {
                                notificationService.error(ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR);
                            }
                        })
                        .build()
        ).open();
    }
}
