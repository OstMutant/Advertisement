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
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.card.AdvertisementCardMetaPanel;
import org.ost.advertisement.ui.views.components.buttons.DeleteActionButton;
import org.ost.advertisement.ui.views.components.buttons.EditActionButton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementCardView extends VerticalLayout {

    private final transient I18nService i18n;
    private final transient AdvertisementCardMetaPanel.Builder metaPanelBuilder;
    private final transient EditActionButton.Builder editButtonBuilder;
    private final transient DeleteActionButton.Builder deleteButtonBuilder;
    private final transient AccessEvaluator access;

    private AdvertisementCardView setupContent(AdvertisementInfoDto ad,
                                               Runnable onSelect,
                                               Runnable onEdit,
                                               Runnable onDelete) {
        addClassName("advertisement-card");
        getElement().setAttribute("tabindex", "0");

        // only fire onSelect when click is NOT on a button inside the card
        getElement().addEventListener("click", _ -> onSelect.run())
                .setFilter("!event.target.closest('button')");
        getElement().addEventListener("keydown", _ -> onSelect.run())
                .setFilter("(event.key === 'Enter' || event.key === ' ') && !event.target.closest('button')");

        Span spacer = new Span();
        setFlexGrow(1, spacer);

        add(createTitle(ad), createDescription(ad), spacer, createMeta(ad), createActions(ad, onEdit, onDelete));
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

    private AdvertisementCardMetaPanel createMeta(AdvertisementInfoDto ad) {
        boolean neverEdited = ad.getUpdatedAt() == null || ad.getUpdatedAt().equals(ad.getCreatedAt());
        return metaPanelBuilder.build(AdvertisementCardMetaPanel.Parameters.builder()
                .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                .authorEmail(ad.getCreatedByUserEmail())
                .dateLabel(neverEdited ? i18n.get(ADVERTISEMENT_CARD_CREATED) : i18n.get(ADVERTISEMENT_CARD_UPDATED))
                .date(neverEdited ? ad.getCreatedAt() : ad.getUpdatedAt())
                .build());
    }

    private HorizontalLayout createActions(AdvertisementInfoDto ad, Runnable onEdit, Runnable onDelete) {
        Button edit = editButtonBuilder.build(
                EditActionButton.Config.builder()
                        .tooltip(i18n.get(ADVERTISEMENT_CARD_BUTTON_EDIT))
                        .onClick(onEdit)
                        .small(true)
                        .cssClassName("advertisement-edit")
                        .build()
        );

        Button delete = deleteButtonBuilder.build(
                DeleteActionButton.Config.builder()
                        .tooltip(i18n.get(ADVERTISEMENT_CARD_BUTTON_DELETE))
                        .onClick(onDelete)
                        .small(true)
                        .cssClassName("advertisement-delete")
                        .build()
        );

        boolean canOperate = access.canOperate(ad);
        edit.setVisible(canOperate);
        delete.setVisible(canOperate);

        HorizontalLayout actions = new HorizontalLayout(edit, delete);
        actions.addClassName("advertisement-actions");
        return actions;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<AdvertisementCardView> cardProvider;

        public AdvertisementCardView build(AdvertisementInfoDto ad,
                                           Runnable onSelect,
                                           Runnable onEdit,
                                           Runnable onDelete) {
            return cardProvider.getObject().setupContent(ad, onSelect, onEdit, onDelete);
        }
    }
}