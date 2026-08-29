package org.ost.marketplace.ui.views.main.header.account;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
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
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.overlay.AbstractViewOverlayModeHandler;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.springframework.context.annotation.Scope;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/**
 * {@code AccountOverlay}'s Provider Profile section, View mode -- the default entry point,
 * mirroring the Advertisement/Taxon/City domains' own View/Edit split. Shows an empty state with
 * a Create action when the actor has no profile yet; the Edit/Create button is visible only per
 * {@link AccessEvaluator#canEditUserAccount} (see {@code improvement-178}).
 */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class ProviderProfileViewModeHandler extends AbstractViewOverlayModeHandler
        implements Configurable<ProviderProfileViewModeHandler, ProviderProfileViewModeHandler.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull Long targetUserId;
        @NonNull Runnable onEdit;
        @NonNull Runnable onClose;
        @NonNull Component tabBar;
    }

    private final ProviderProfileSaveService providerProfileSaveService;
    private final AccessEvaluator            access;
    @Getter
    private final I18nService                i18nService;

    private Parameters params;
    private ProviderProfileDto profile;

    @Override
    public ProviderProfileViewModeHandler configure(Parameters p) {
        this.params = p;
        return this;
    }

    @Override
    protected Div buildPrimaryContent() {
        profile = providerProfileSaveService.findByActorId(params.getTargetUserId()).orElse(null);

        Div cardHeader = new Div(VaadinIcon.BRIEFCASE.create(), new Span(getValue(PROVIDER_PROFILE_OVERLAY_SECTION_LABEL)));
        cardHeader.addClassName("overlay__view-card-header");

        Div card = profile == null ? buildEmptyCard(cardHeader) : buildProfileCard(cardHeader, profile);
        card.addClassName("overlay__view-card");

        return new Div(params.getTabBar(), card);
    }

    private Div buildEmptyCard(Div cardHeader) {
        Span emptyText = new Span(getValue(PROVIDER_PROFILE_VIEW_EMPTY_TEXT));
        emptyText.addClassName("provider-profile-view-empty-text");
        return new Div(cardHeader, emptyText);
    }

    private Div buildProfileCard(Div cardHeader, ProviderProfileDto profile) {
        Span kindBadge = new Span(profile.getKind().name());
        kindBadge.addClassName("provider-profile-kind-badge");
        kindBadge.addClassName("provider-profile-kind-badge--" + profile.getKind().name().toLowerCase());

        Div about = new Div();
        about.addClassName("overlay__view-description");
        about.getElement().setProperty("innerHTML", profile.getAbout() != null ? profile.getAbout() : "");

        Div card = new Div(cardHeader, kindBadge, about);
        buildChipRow(card, profile.getCategoryNames(), "provider-profile-categories-chips",
                "provider-profile-category-chip", getValue(PROVIDER_PROFILE_OVERLAY_FIELD_CATEGORIES));
        if (profile.getCityName() != null) {
            buildChipRow(card, List.of(profile.getCityName()), "provider-profile-city-chips",
                    "provider-profile-city-chip", getValue(PROVIDER_PROFILE_OVERLAY_FIELD_CITY));
        }
        return card;
    }

    private static void buildChipRow(Div card, List<String> names, String rowCssClass, String chipCssClass, String ariaLabel) {
        if (names == null || names.isEmpty()) return;
        Div row = new Div();
        row.addClassName(rowCssClass);
        row.getElement().setAttribute("role", "list");
        row.getElement().setAttribute("aria-label", ariaLabel);
        names.forEach(name -> {
            Span chip = new Span(name);
            chip.addClassName(chipCssClass);
            chip.getElement().setAttribute("role", "listitem");
            row.add(chip);
        });
        card.add(row);
    }

    @Override
    protected Div buildHeaderActions() {
        UiPrimaryButton editButton = new UiPrimaryButton(getValue(profile == null
                ? PROVIDER_PROFILE_VIEW_BUTTON_CREATE : PROVIDER_PROFILE_VIEW_BUTTON_EDIT));
        UiIconButton closeButton = new UiIconButton(getValue(HEADER_HOME), VaadinIcon.CLOSE.create());
        editButton.addClickListener(_  -> params.getOnEdit().run());
        closeButton.addClickListener(_ -> params.getOnClose().run());
        editButton.setVisible(access.canEditUserAccount(params.getTargetUserId()));
        return new Div(editButton, closeButton);
    }
}
