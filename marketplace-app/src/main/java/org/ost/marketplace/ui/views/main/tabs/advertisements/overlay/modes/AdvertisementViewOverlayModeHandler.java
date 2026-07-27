package org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractViewOverlayModeHandler;
import org.ost.marketplace.ui.views.components.attachment.AttachmentGalleryService;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.elements.OverlayAdvertisementMetaPanel;
import org.ost.marketplace.ui.views.services.AppLinkService;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.utils.ShareUtil;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

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
    }

    private final AccessEvaluator                                   access;
    @Getter
    private final I18nService                                       i18nService;
    private final OverlayAdvertisementMetaPanel                     metaPanel;
    private final ComponentFactory<AttachmentPort>                  attachmentPortFactory;
    private final ComponentFactory<AttachmentGalleryService>      galleryServiceFactory;
    private final ComponentFactory<TaxonPort>                       taxonPortFactory;
    private final LocaleProvider                                    localeProvider;
    private final AppLinkService                                    appLinkService;
    private final NotificationService                               notificationService;

    private Parameters params;

    @Override
    public AdvertisementViewOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    protected Div buildPrimaryContent() {
        H2 title = new H2(params.getAd().getTitle());
        title.addClassName("overlay__view-title");

        Div description = new Div();
        description.addClassName("overlay__view-description");
        description.getElement().setProperty("innerHTML", params.getAd().getDescription() != null ? params.getAd().getDescription() : "");

        Div cardHeader = new Div(VaadinIcon.EYE.create(), new Span(getValue(ADVERTISEMENT_OVERLAY_SECTION_VIEW)));
        cardHeader.addClassName("overlay__view-card-header");
        cardHeader.addClassName("overlay__view-card-header--" + params.getAd().getAdKind().name().toLowerCase());

        Div textCard = new Div(cardHeader, title, description);
        textCard.addClassName("overlay__view-card");
        textCard.addClassName("overlay__view-card--" + params.getAd().getAdKind().name().toLowerCase());

        taxonPortFactory.ifAvailable(taxonPort -> {
            var taxons = taxonPort.getForEntity(EntityType.ADVERTISEMENT, params.getAd().getId(), localeProvider.getCurrentLocale());
            buildChipRow(textCard, taxons, TaxonType.CATEGORY, "advertisement-categories-chips",
                    "advertisement-category-chip", getValue(ADVERTISEMENT_OVERLAY_FIELD_CATEGORIES));
            buildChipRow(textCard, taxons, TaxonType.CITY, "advertisement-city-chips",
                    "advertisement-city-chip", getValue(ADVERTISEMENT_OVERLAY_FIELD_CITY));
        });

        textCard.add(buildAdKindBadge(params.getAd()));

        Div viewBody = new Div(textCard);
        attachmentPortFactory.ifAvailable(_ -> {
            var gallery = galleryServiceFactory.get().buildGalleryForView(new EntityRef(EntityType.ADVERTISEMENT, params.getAd().getId()));
            gallery.addClassName("attachment-gallery--" + params.getAd().getAdKind().name().toLowerCase());
            viewBody.add(gallery);
        });
        viewBody.add(metaPanel.configure(OverlayAdvertisementMetaPanel.Parameters.from(params.getAd())));
        viewBody.addClassName("overlay__view-body");

        return viewBody;
    }

    private Span buildAdKindBadge(AdvertisementInfoDto ad) {
        Span badge = new Span(getValue(forAdKind(ad.getAdKind())));
        badge.addClassName("advertisement-ad-kind-badge");
        badge.addClassName("advertisement-ad-kind-badge--" + ad.getAdKind().name().toLowerCase());
        return badge;
    }

    private static void buildChipRow(Div textCard, List<TaxonDto> taxons, TaxonType type,
                                      String rowCssClass, String chipCssClass, String ariaLabel) {
        List<TaxonDto> matching = taxons.stream().filter(t -> t.getType() == type).toList();
        if (matching.isEmpty()) return;
        Div row = new Div();
        row.addClassName(rowCssClass);
        row.getElement().setAttribute("role", "list");
        row.getElement().setAttribute("aria-label", ariaLabel);
        matching.forEach(taxon -> {
            Span chip = new Span(taxon.getName());
            chip.addClassName(chipCssClass);
            if (taxon.isDeleted()) chip.addClassName(chipCssClass + "--deleted");
            chip.getElement().setAttribute("role", "listitem");
            row.add(chip);
        });
        textCard.add(row);
    }

    @Override
    protected Div buildHeaderActions() {
        UiPrimaryButton editButton = new UiPrimaryButton(getValue(ADVERTISEMENT_CARD_BUTTON_EDIT));
        UiIconButton shareButton = new UiIconButton(getValue(ADVERTISEMENT_CARD_BUTTON_SHARE), VaadinIcon.SHARE.create());
        shareButton.addClassName("overlay__view-share");
        UiIconButton closeButton = new UiIconButton(getValue(MAIN_TAB_ADVERTISEMENTS), VaadinIcon.CLOSE.create());
        editButton.addClickListener(_  -> params.getOnEdit().run());
        shareButton.addClickListener(_ -> ShareUtil.share(shareButton, appLinkService.advertisementUrl(params.getAd().getId()),
                params.getAd().getTitle(), () -> notificationService.success(ADVERTISEMENT_CARD_NOTIFICATION_LINK_COPIED)));
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.canOperate(params.getAd().getOwnerUserId()));
        return new Div(editButton, shareButton, closeButton);
    }

}
