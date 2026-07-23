package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractViewOverlayModeHandler;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.context.annotation.Scope;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class TaxonViewOverlayModeHandler extends AbstractViewOverlayModeHandler
        implements Configurable<TaxonViewOverlayModeHandler, TaxonViewOverlayModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull TaxonDto  taxon;
        @NonNull Runnable  onEdit;
        @NonNull Runnable  onClose;
    }

    @Getter
    private final I18nService                       i18nService;
    private final AccessEvaluator                   access;
    private final ComponentFactory<TaxonPort>       taxonPortFactory;

    private Parameters params;

    @Override
    public TaxonViewOverlayModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    protected Div buildPrimaryContent() {
        List<TaxonTranslationDto> translations = taxonPortFactory.findIfAvailable()
                .map(p -> p.getTranslations(params.getTaxon().getId()))
                .orElse(List.of());

        String nameEn = "";
        String descEn = "";
        String nameUk = "";
        String descUk = "";
        for (TaxonTranslationDto t : translations) {
            if ("en".equals(t.getLocale())) { nameEn = t.getName(); descEn = t.getDescription(); }
            else if ("uk".equals(t.getLocale())) { nameUk = t.getName(); descUk = t.getDescription(); }
        }

        Div cardHeader = new Div(VaadinIcon.TAG.create(), new Span(getValue(TAXON_OVERLAY_SECTION_LABEL)));
        cardHeader.addClassName("overlay__view-card-header");

        H4 enLabel = new H4(getValue(TAXON_OVERLAY_LOCALE_TAB_EN));
        enLabel.addClassName("taxon-locale-label");
        Div enContent = buildLocaleContent(nameEn, descEn);
        enContent.addClassName("taxon-locale-content");

        H4 ukLabel = new H4(getValue(TAXON_OVERLAY_LOCALE_TAB_UK));
        ukLabel.addClassName("taxon-locale-label");
        Div ukContent = buildLocaleContent(nameUk, descUk);
        ukContent.addClassName("taxon-locale-content");

        Div card = new Div(cardHeader, enLabel, enContent, ukLabel, ukContent);
        card.addClassName("overlay__form-fields-card");

        Div body = new Div(card);
        body.addClassName("overlay__view-body");
        return body;
    }

    private Div buildLocaleContent(String name, String description) {
        H2 nameHeading = new H2(name);
        nameHeading.addClassName("taxon-view-name");

        Span descSpan = new Span(description);
        descSpan.addClassName("taxon-view-description");

        return new Div(nameHeading, descSpan);
    }

    @Override
    protected Div buildHeaderActions() {
        UiPrimaryButton editButton = new UiPrimaryButton(getValue(TAXON_VIEW_BUTTON_EDIT));
        UiIconButton closeButton = new UiIconButton(getValue(TAXON_OVERLAY_BUTTON_CANCEL), VaadinIcon.CLOSE.create());
        editButton.addClickListener(_  -> params.getOnEdit().run());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.isPrivileged());
        return new Div(editButton, closeButton);
    }
}
