package org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractViewOverlayModeHandler;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonTranslationDto;

import java.util.List;

/** Shared view-mode rendering for a taxon-backed entity (locale card + edit/close header actions), parameterized by the concrete City/Category subclass's data source, labels, and callbacks. */
public abstract class AbstractTaxonViewOverlayModeHandler extends AbstractViewOverlayModeHandler implements I18nParams {

    /** The domain-specific i18n keys this handler needs -- everything else is shared across every taxon type. */
    public record Labels(I18nKey sectionLabel, I18nKey localeTabEn, I18nKey localeTabUk,
                          I18nKey viewButtonEdit, I18nKey overlayButtonCancel) {}

    protected abstract TaxonCatalogService getTaxonCatalogService();
    protected abstract AccessEvaluator     getAccess();
    protected abstract Long                getEntityId();
    protected abstract Runnable            getOnEdit();
    protected abstract Runnable            getOnClose();
    protected abstract Labels              getLabels();

    @Override
    protected Div buildPrimaryContent() {
        List<TaxonTranslationDto> translations = getTaxonCatalogService().getTranslations(getEntityId());

        String nameEn = "";
        String descEn = "";
        String nameUk = "";
        String descUk = "";
        for (TaxonTranslationDto t : translations) {
            if ("en".equals(t.getLocale())) { nameEn = t.getName(); descEn = t.getDescription(); }
            else if ("uk".equals(t.getLocale())) { nameUk = t.getName(); descUk = t.getDescription(); }
        }

        Div cardHeader = new Div(VaadinIcon.TAG.create(), new Span(getValue(getLabels().sectionLabel())));
        cardHeader.addClassName("overlay__view-card-header");

        H4 enLabel = new H4(getValue(getLabels().localeTabEn()));
        enLabel.addClassName("taxon-locale-label");
        Div enContent = buildLocaleContent(nameEn, descEn);
        enContent.addClassName("taxon-locale-content");

        H4 ukLabel = new H4(getValue(getLabels().localeTabUk()));
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
        UiPrimaryButton editButton = new UiPrimaryButton(getValue(getLabels().viewButtonEdit()));
        UiIconButton closeButton = new UiIconButton(getValue(getLabels().overlayButtonCancel()), VaadinIcon.CLOSE.create());
        editButton.addClickListener(_  -> getOnEdit().run());
        closeButton.addClickListener(_ -> getOnClose().run());
        editButton.setVisible(getAccess().isPrivileged());
        return new Div(editButton, closeButton);
    }
}
