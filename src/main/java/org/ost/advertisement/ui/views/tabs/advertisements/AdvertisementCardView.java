package org.ost.advertisement.ui.views.tabs.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.services.NotificationService;
import org.ost.advertisement.ui.views.utils.builder.Configurable;
import org.ost.advertisement.ui.views.utils.builder.ComponentBuilder;
import org.ost.advertisement.ui.views.tabs.advertisements.card.AdvertisementCardMetaPanel;
import org.ost.advertisement.ui.views.tabs.advertisements.overlay.AdvertisementOverlay;
import org.ost.advertisement.ui.views.components.buttons.action.DeleteActionButton;
import org.ost.advertisement.ui.views.components.buttons.action.EditActionButton;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmActionDialog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementCardView extends VerticalLayout
        implements Configurable<AdvertisementCardView, AdvertisementCardView.Parameters> {

    private static final String CLICK_EVENT = "click";

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull AdvertisementInfoDto ad;
        @NonNull Runnable             onChanged;
    }

    private final transient I18nService                        i18n;
    private final transient NotificationService                notificationService;
    private final transient AdvertisementService               advertisementService;
    private final transient AdvertisementCardMetaPanel.Builder metaPanelBuilder;
    private final transient EditActionButton.Builder           editButtonBuilder;
    private final transient DeleteActionButton.Builder         deleteButtonBuilder;
    private final transient AccessEvaluator                    access;
    private final transient ConfirmActionDialog.Builder        confirmActionDialogBuilder;
    private final transient AdvertisementOverlay               overlay;

    @Override
    public AdvertisementCardView configure(Parameters p) {
        AdvertisementInfoDto ad        = p.getAd();
        Runnable             onChanged = p.getOnChanged();

        addClassName("advertisement-card");

        getElement().addEventListener(CLICK_EVENT, _ -> overlay.openForView(ad, onChanged));
        getElement().setAttribute("tabindex", "0");
        getElement().addEventListener("keydown", _ -> overlay.openForView(ad, onChanged))
                .setFilter("event.key === 'Enter' || event.key === ' '");

        Span spacer = new Span();
        setFlexGrow(1, spacer);

        add(createTitle(ad),
                createDescription(ad),
                spacer,
                createMetaPanel(ad),
                createActions(ad, onChanged));

        return this;
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

        return metaPanelBuilder.build(AdvertisementCardMetaPanel.Parameters.builder()
                .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                .authorEmail(ad.getCreatedByUserEmail())
                .dateLabel(neverEdited
                        ? i18n.get(ADVERTISEMENT_CARD_CREATED)
                        : i18n.get(ADVERTISEMENT_CARD_UPDATED))
                .date(neverEdited ? ad.getCreatedAt() : ad.getUpdatedAt())
                .build());
    }

    private HorizontalLayout createActions(AdvertisementInfoDto ad, Runnable onChanged) {
        boolean canOperate = access.canOperate(ad);

        Button edit   = createEditButton(ad, onChanged, canOperate);
        Button delete = createDeleteButton(ad, onChanged, canOperate);

        HorizontalLayout actions = new HorizontalLayout(edit, delete);
        actions.addClassName("advertisement-actions");
        return actions;
    }

    private Button createEditButton(AdvertisementInfoDto ad, Runnable onChanged, boolean visible) {
        Button edit = editButtonBuilder.build(
                EditActionButton.Parameters.builder()
                        .tooltip(i18n.get(ADVERTISEMENT_CARD_BUTTON_EDIT))
                        .onClick(() -> overlay.openForEdit(ad, onChanged))
                        .small(true)
                        .cssClassName("advertisement-edit")
                        .build()
        );
        edit.setVisible(visible);
        edit.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData("event.stopPropagation()");
        return edit;
    }

    private Button createDeleteButton(AdvertisementInfoDto ad, Runnable onChanged, boolean visible) {
        Button delete = deleteButtonBuilder.build(
                DeleteActionButton.Parameters.builder()
                        .tooltip(i18n.get(ADVERTISEMENT_CARD_BUTTON_DELETE))
                        .onClick(() -> confirmAndDelete(ad, onChanged))
                        .small(true)
                        .cssClassName("advertisement-delete")
                        .build()
        );
        delete.setVisible(visible);
        delete.getElement().addEventListener(CLICK_EVENT, _ -> {}).addEventData("event.stopPropagation()");
        return delete;
    }

    private void confirmAndDelete(AdvertisementInfoDto ad, Runnable onChanged) {
        confirmActionDialogBuilder.build(
                ConfirmActionDialog.Parameters.builder()
                        .titleKey(ADVERTISEMENT_VIEW_CONFIRM_DELETE_TITLE)
                        .message(i18n.get(ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT, ad.getTitle(), ad.getId()))
                        .confirmKey(ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON)
                        .cancelKey(ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON)
                        .onConfirm(() -> {
                            try {
                                advertisementService.delete(ad);
                                notificationService.success(ADVERTISEMENT_VIEW_NOTIFICATION_DELETED);
                                onChanged.run();
                            } catch (Exception ex) {
                                notificationService.error(ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage());
                            }
                        })
                        .build()
        ).open();
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<AdvertisementCardView, Parameters> {
        @Getter
        private final ObjectProvider<AdvertisementCardView> provider;
    }
}