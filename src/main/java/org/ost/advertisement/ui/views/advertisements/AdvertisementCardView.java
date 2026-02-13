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

        H3 title = createTitle(ad);
        Span description = createDescription(ad);
        Button toggle = createToggle(ad);
        VerticalLayout meta = createMeta(ad);
        HorizontalLayout actions = createActions(ad, refreshAdvertisements);

        add(title, description, toggle, meta, actions);
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

    private Button createToggle(AdvertisementInfoDto ad) {
        Button toggle = new Button("Read more");
        toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        toggle.addClassName("advertisement-toggle");
        toggle.addClickListener(_ -> new AdvertisementDescriptionDialog(ad.getTitle(), ad.getDescription()).open());
        return toggle;
    }

    private VerticalLayout createMeta(AdvertisementInfoDto ad) {
        Span createdAt = new Span(i18n.get(ADVERTISEMENT_CARD_CREATED) + " " + TimeZoneUtil.formatInstant(ad.getCreatedAt()));
        Span updatedAt = new Span(i18n.get(ADVERTISEMENT_CARD_UPDATED) + " " + TimeZoneUtil.formatInstant(ad.getUpdatedAt()));
        Span userId = new Span(i18n.get(ADVERTISEMENT_CARD_USER) + " " + ad.getCreatedByUserId());

        VerticalLayout meta = new VerticalLayout(createdAt, updatedAt, userId);
        meta.addClassName("advertisement-meta");
        return meta;
    }

    private HorizontalLayout createActions(AdvertisementInfoDto ad, Runnable refreshAdvertisements) {
        Button edit = new Button(i18n.get(ADVERTISEMENT_CARD_BUTTON_EDIT), VaadinIcon.EDIT.create());
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        edit.addClassName("advertisement-edit");
        edit.addClickListener(_ -> upsertDialogBuilder.buildAndOpen(ad, refreshAdvertisements));

        Button delete = new Button(i18n.get(ADVERTISEMENT_CARD_BUTTON_DELETE), VaadinIcon.TRASH.create());
        delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
        delete.addClassName("advertisement-delete");
        delete.addClickListener(_ -> openConfirmDeleteDialog(ad, refreshAdvertisements));

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
