package org.ost.marketplace.ui.views.main.tabs.providers.overlay;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractViewOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.providers.ProviderProfileDeleteUtil;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.services.AppLinkService;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.utils.HtmlExcerptUtil;
import org.ost.marketplace.ui.views.utils.ShareUtil;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.orchestrator.services.TaxonLookupService;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.springframework.context.annotation.Scope;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/**
 * Read-only view of a provider profile inside the public Providers catalog. Never enters an edit
 * mode -- editing a provider profile already has its own dedicated path (AccountOverlay's
 * Provider Profile tab) -- so this handler exposes Share and Delete actions only, no Edit and no
 * history button.
 */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ProviderProfileCatalogViewModeHandler extends AbstractViewOverlayModeHandler
        implements Configurable<ProviderProfileCatalogViewModeHandler, ProviderProfileCatalogViewModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull ProviderProfileDto profile;
        @NonNull Runnable           onDeleted;
        @NonNull Runnable           onClose;
    }

    private final AccessEvaluator            access;
    @Getter
    private final I18nService                i18nService;
    private final ProviderProfileSaveService providerProfileSaveService;
    private final TaxonLookupService         taxonLookupService;
    private final LocaleProvider             localeProvider;
    private final AppLinkService             appLinkService;
    private final NotificationService        notificationService;

    private Parameters params;

    @Override
    public ProviderProfileCatalogViewModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    protected Div buildPrimaryContent() {
        ProviderProfileDto profile = params.getProfile();

        Span kindBadge = new Span(getValue(forProviderKind(profile.getKind())));
        kindBadge.addClassName("provider-profile-kind-badge");
        kindBadge.addClassName("provider-profile-kind-badge--" + profile.getKind().name().toLowerCase());

        Div cardHeader = new Div(VaadinIcon.BRIEFCASE.create(), new Span(getValue(PROVIDER_PROFILE_OVERLAY_SECTION_LABEL)));
        cardHeader.addClassName("overlay__view-card-header");

        Div about = new Div();
        about.addClassName("overlay__view-description");
        about.getElement().setProperty("innerHTML", profile.getAbout() != null ? profile.getAbout() : "");

        Div textCard = new Div(cardHeader, kindBadge, about);
        textCard.addClassName("overlay__view-card");

        var taxons = taxonLookupService.getForEntity(EntityType.PROVIDER_PROFILE, profile.getId(), localeProvider.getCurrentLocale());
        buildChipRow(textCard, taxons, TaxonType.CATEGORY, "provider-profile-categories-chips",
                "provider-profile-category-chip", getValue(PROVIDER_PROFILE_OVERLAY_FIELD_CATEGORIES));
        buildChipRow(textCard, taxons, TaxonType.CITY, "provider-profile-city-chips",
                "provider-profile-city-chip", getValue(PROVIDER_PROFILE_OVERLAY_FIELD_CITY));

        return new Div(textCard);
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
            chip.getElement().setAttribute("role", "listitem");
            row.add(chip);
        });
        textCard.add(row);
    }

    @Override
    protected Div buildHeaderActions() {
        ProviderProfileDto profile = params.getProfile();

        UiIconButton shareButton = new UiIconButton(getValue(PROVIDERS_CATALOG_OVERLAY_SHARE), VaadinIcon.SHARE.create());
        shareButton.addClassName("overlay__view-share");
        shareButton.addClickListener(_ -> ShareUtil.share(shareButton, appLinkService.providerProfileUrl(profile.getId()),
                HtmlExcerptUtil.plainText(profile.getAbout()), () -> notificationService.success(PROVIDERS_CARD_NOTIFICATION_LINK_COPIED)));

        UiIconButton deleteButton = new UiIconButton(getValue(PROVIDERS_CATALOG_OVERLAY_DELETE), VaadinIcon.TRASH.create());
        deleteButton.addClassName("overlay__view-delete");
        deleteButton.addClickListener(_ -> confirmAndDelete(profile));
        deleteButton.setVisible(access.canEditUserAccount(profile.getActorId()));

        UiIconButton closeButton = new UiIconButton(getValue(MAIN_TAB_PROVIDERS), VaadinIcon.CLOSE.create());
        closeButton.addClickListener(_ -> params.getOnClose().run());

        return new Div(shareButton, deleteButton, closeButton);
    }

    private void confirmAndDelete(ProviderProfileDto profile) {
        ProviderProfileDeleteUtil.confirmAndDelete(i18nService, notificationService, providerProfileSaveService, access, profile, params.getOnDeleted());
    }
}
