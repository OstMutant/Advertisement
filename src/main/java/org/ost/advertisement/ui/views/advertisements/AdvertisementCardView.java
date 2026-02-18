package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.AllArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.advertisements.dialogs.AdvertisementDescriptionDialog;
import org.ost.advertisement.ui.views.advertisements.dialogs.AdvertisementUpsertDialog;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmDeleteHelper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@AllArgsConstructor
public class AdvertisementCardView extends VerticalLayout {

    private final transient I18nService i18n;
    private final transient AdvertisementService advertisementService;
    private final transient AdvertisementUpsertDialog.Builder upsertDialogBuilder;

    private AdvertisementCardView setupContent(AdvertisementInfoDto ad, Runnable refreshAdvertisements) {
        addClassName("advertisement-card");

        getElement().addEventListener("click", _ -> openDescriptionDialog(ad));

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
        new AdvertisementDescriptionDialog(i18n, ad.getTitle(), ad.getDescription()).open();
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
        String updatedAt = TimeZoneUtil.formatInstantHuman(ad.getUpdatedAt());

        Span meta = new Span(userName + " · " + i18n.get(ADVERTISEMENT_CARD_UPDATED) + " " + updatedAt);
        meta.addClassName("advertisement-meta");
        return meta;
    }

    private HorizontalLayout createActions(AdvertisementInfoDto ad, Runnable refreshAdvertisements) {
        Button edit = new Button(VaadinIcon.EDIT.create());
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        edit.addClassName("advertisement-edit");
        edit.getElement().setAttribute("title", i18n.get(ADVERTISEMENT_CARD_BUTTON_EDIT));
        edit.addClickListener(_ -> upsertDialogBuilder.buildAndOpen(ad, refreshAdvertisements));
        edit.getElement().addEventListener("click", _ -> {}).addEventData("event.stopPropagation()");

        Button delete = new Button(VaadinIcon.TRASH.create());
        delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        delete.addClassName("advertisement-delete");
        delete.getElement().setAttribute("title", i18n.get(ADVERTISEMENT_CARD_BUTTON_DELETE));
        delete.addClickListener(_ -> openConfirmDeleteDialog(ad, refreshAdvertisements));
        delete.getElement().addEventListener("click", _ -> {}).addEventData("event.stopPropagation()");

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
    @AllArgsConstructor
    public static class Builder {
        private final I18nService i18n;
        private final AdvertisementService advertisementService;
        private final AdvertisementUpsertDialog.Builder upsertDialogBuilder;
        private final ObjectProvider<AdvertisementCardView> cardProvider;

        public AdvertisementCardView build(AdvertisementInfoDto ad, Runnable refresh) {
            AdvertisementCardView cardView = cardProvider.getObject(i18n, advertisementService, upsertDialogBuilder);
            return cardView.setupContent(ad, refresh);
        }
    }
}