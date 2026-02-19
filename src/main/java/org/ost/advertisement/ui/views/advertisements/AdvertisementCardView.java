package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.advertisements.dialogs.AdvertisementDescriptionDialog;
import org.ost.advertisement.ui.views.advertisements.dialogs.AdvertisementUpsertDialog;
import org.ost.advertisement.ui.views.components.buttons.DeleteActionButton;
import org.ost.advertisement.ui.views.components.buttons.EditActionButton;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmDeleteHelper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementCardView extends VerticalLayout {

    private final transient I18nService i18n;
    private final transient AdvertisementService advertisementService;
    private final transient AdvertisementUpsertDialog.Builder upsertDialogBuilder;
    private final transient EditActionButton.Builder editButtonBuilder;
    private final transient DeleteActionButton.Builder deleteButtonBuilder;
    private final transient AccessEvaluator access;

    private AdvertisementCardView setupContent(AdvertisementInfoDto ad, Runnable refreshAdvertisements) {
        addClassName("advertisement-card");

        getElement().addEventListener("click", _ -> openDescriptionDialog(ad));

        getElement().setAttribute("tabindex", "0");
        getElement().addEventListener("keydown", _ -> openDescriptionDialog(ad))
                .setFilter("event.key === 'Enter' || event.key === ' '");

        H3 title = createTitle(ad);
        Span description = createDescription(ad);
        Span meta = createMeta(ad);
        HorizontalLayout actions = createActions(ad, refreshAdvertisements);

        Span spacer = new Span();
        setFlexGrow(1, spacer);

        add(title, description, spacer, meta, actions);
        return this;
    }

    private void openDescriptionDialog(AdvertisementInfoDto ad) {
        new AdvertisementDescriptionDialog(i18n, ad).open();
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

    private Span createMeta(AdvertisementInfoDto ad) {
        String userName = ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—";

        boolean neverEdited = ad.getUpdatedAt() == null || ad.getUpdatedAt().equals(ad.getCreatedAt());
        String dateLabel = neverEdited
                ? i18n.get(ADVERTISEMENT_CARD_CREATED)
                : i18n.get(ADVERTISEMENT_CARD_UPDATED);
        String dateValue = TimeZoneUtil.formatInstantHuman(
                neverEdited ? ad.getCreatedAt() : ad.getUpdatedAt()
        );

        Span authorSpan = new Span(userName);
        authorSpan.addClassName("advertisement-meta-author");
        if (ad.getCreatedByUserEmail() != null) {
            authorSpan.getElement().setAttribute("title", ad.getCreatedByUserEmail());
        }

        Span separator = new Span(" · ");
        separator.addClassName("advertisement-meta-separator");

        Span dateSpan = new Span(dateLabel + " " + dateValue);
        dateSpan.addClassName("advertisement-meta-date");

        Span meta = new Span(authorSpan, separator, dateSpan);
        meta.addClassName("advertisement-meta");
        return meta;
    }

    private HorizontalLayout createActions(AdvertisementInfoDto ad, Runnable refreshAdvertisements) {
        boolean canOperate = access.canOperate(ad);

        Button edit = editButtonBuilder.build(
                EditActionButton.Config.builder()
                        .tooltip(i18n.get(ADVERTISEMENT_CARD_BUTTON_EDIT))
                        .onClick(() -> upsertDialogBuilder.buildAndOpen(ad, refreshAdvertisements))
                        .small(true)
                        .cssClassName("advertisement-edit")
                        .build()
        );

        Button delete = deleteButtonBuilder.build(
                DeleteActionButton.Config.builder()
                        .tooltip(i18n.get(ADVERTISEMENT_CARD_BUTTON_DELETE))
                        .onClick(() -> openConfirmDeleteDialog(ad, refreshAdvertisements))
                        .small(true)
                        .cssClassName("advertisement-delete")
                        .build()
        );

        edit.setVisible(canOperate);
        delete.setVisible(canOperate);

        HorizontalLayout actions = new HorizontalLayout(edit, delete);
        actions.addClassName("advertisement-actions");
        return actions;
    }

    private void openConfirmDeleteDialog(AdvertisementInfoDto ad, Runnable refreshAdvertisements) {
        ConfirmDeleteHelper.showConfirm(
                i18n,
                i18n.get(ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT, ad.getTitle(), ad.getId()),
                ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON,
                ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON,
                () -> {
                    try {
                        advertisementService.delete(ad);
                        NotificationType.SUCCESS.show(i18n.get(ADVERTISEMENT_VIEW_NOTIFICATION_DELETED));
                        refreshAdvertisements.run();
                    } catch (Exception ex) {
                        NotificationType.ERROR.show(
                                i18n.get(ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage())
                        );
                    }
                }
        );
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<AdvertisementCardView> cardProvider;

        public AdvertisementCardView build(AdvertisementInfoDto ad, Runnable refresh) {
            return cardProvider.getObject().setupContent(ad, refresh);
        }
    }
}