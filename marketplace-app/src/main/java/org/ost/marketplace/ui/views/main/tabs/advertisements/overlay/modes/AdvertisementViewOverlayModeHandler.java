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
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractViewOverlayModeHandler;
import org.ost.marketplace.ui.views.components.attachment.AttachmentGalleryService;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.elements.OverlayAdvertisementMetaPanel;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

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
    private final UiPrimaryButton                                   editButton;
    private final UiIconButton                                      closeButton;
    private final UiComponentFactory<AttachmentGalleryService> galleryServiceFactory;

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

        Span description = new Span(params.getAd().getDescription());
        description.addClassName("overlay__view-description");

        Div cardHeader = new Div(VaadinIcon.EYE.create(), new Span(getValue(ADVERTISEMENT_OVERLAY_SECTION_VIEW)));
        cardHeader.addClassName("overlay__view-card-header");

        Div textCard = new Div(cardHeader, title, description);
        textCard.addClassName("overlay__view-card");

        Div viewBody = new Div(textCard);
        galleryServiceFactory.ifAvailable(ext -> viewBody.add(
                ext.buildGalleryForView(new EntityRef(EntityType.ADVERTISEMENT, params.getAd().getId()))));
        viewBody.add(metaPanel.configure(OverlayAdvertisementMetaPanel.Parameters.from(params.getAd())));
        viewBody.addClassName("overlay__view-body");

        return viewBody;
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
        editButton.setVisible(access.canOperate(params.getAd().getOwnerUserId()));
        return new Div(editButton, closeButton);
    }

}
