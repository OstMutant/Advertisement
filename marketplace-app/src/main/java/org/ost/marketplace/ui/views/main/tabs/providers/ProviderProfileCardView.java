package org.ost.marketplace.ui.views.main.tabs.providers;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.EntityMetaPanel;
import org.ost.marketplace.ui.views.components.buttons.action.DeleteActionButton;
import org.ost.marketplace.ui.views.components.buttons.action.ShareActionButton;
import org.ost.marketplace.ui.views.main.tabs.providers.overlay.ProviderProfileCatalogOverlay;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.services.AppLinkService;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.utils.HtmlExcerptUtil;
import org.ost.marketplace.ui.views.utils.ShareUtil;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.springframework.context.annotation.Scope;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** Text-only catalog card for a provider profile (kind badge, about excerpt, category/city lines) -- no photo, since {@link ProviderProfileDto} carries no media field. */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ProviderProfileCardView extends HorizontalLayout
        implements Configurable<ProviderProfileCardView, ProviderProfileCardView.Parameters>, I18nParams, Initialization<ProviderProfileCardView> {

    private static final String CLICK_EVENT = "click";

    @Value @lombok.Builder
    public static class Parameters {
        @NonNull ProviderProfileDto profile;
        @NonNull Runnable           onListChanged;
        @NonNull Runnable           onClosed;
    }

    @Getter
    private final transient I18nService                        i18nService;
    private final transient NotificationService                notificationService;
    private final transient ProviderProfileSaveService          providerProfileSaveService;
    private final transient AccessEvaluator                    access;
    private final transient ProviderProfileCatalogOverlay        overlay;
    private final transient AppLinkService                      appLinkService;
    private final transient UiComponentFactory<EntityMetaPanel> metaPanelFactory;

    @Override
    @PostConstruct
    public ProviderProfileCardView init() {
        addClassName("provider-profile-card");
        return this;
    }

    @Override
    public ProviderProfileCardView configure(Parameters p) {
        ProviderProfileDto profile       = p.getProfile();
        Runnable            onListChanged = p.getOnListChanged();
        Runnable            onClosed      = p.getOnClosed();

        getElement().addEventListener(CLICK_EVENT, _ -> overlay.openForView(profile, onListChanged, onClosed));
        getElement().setAttribute("data-provider-id", String.valueOf(profile.getId()));
        getElement().setAttribute("tabindex", "0");
        getElement().addEventListener("keydown", _ -> overlay.openForView(profile, onListChanged, onClosed))
                .setFilter("event.key === 'Enter' || event.key === ' '");
        addClassName("provider-profile-card--" + profile.getKind().name().toLowerCase());

        add(createContent(profile, onListChanged));

        return this;
    }

    private VerticalLayout createContent(ProviderProfileDto profile, Runnable onListChanged) {
        Span spacer = new Span();

        HorizontalLayout bottom = new HorizontalLayout(createMetaLine(profile), createActions(profile, onListChanged));
        bottom.setWidthFull();
        bottom.setAlignItems(Alignment.END);
        bottom.setJustifyContentMode(JustifyContentMode.BETWEEN);

        VerticalLayout content = new VerticalLayout(createTitle(profile), createAbout(profile), spacer);
        content.addClassName("provider-profile-card-content");
        content.setPadding(false);
        content.setSpacing(false);
        content.setFlexGrow(1, spacer);

        Div categoriesLine = createCategoriesLine(profile);
        if (categoriesLine != null) content.add(categoriesLine);
        Div cityLine = createCityLine(profile);
        if (cityLine != null) content.add(cityLine);
        content.add(createKindBadge(profile));
        content.add(bottom);
        return content;
    }

    private EntityMetaPanel createMetaLine(ProviderProfileDto profile) {
        return metaPanelFactory.build(EntityMetaPanel.Parameters.builder()
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .variant(EntityMetaPanel.Variant.CARD)
                .build());
    }

    private H3 createTitle(ProviderProfileDto profile) {
        H3 title = new H3(profile.getActorName() != null ? profile.getActorName() : getValue(forProviderKind(profile.getKind())));
        title.addClassName("provider-profile-card-title");
        return title;
    }

    private Div createAbout(ProviderProfileDto profile) {
        String text = HtmlExcerptUtil.plainText(profile.getAbout());
        Div about = new Div();
        about.addClassName("provider-profile-card-about");
        about.addClassName("provider-profile-card-about--truncated");
        about.setText(text.isBlank() ? getValue(PROVIDERS_CARD_ABOUT_EMPTY) : text);

        Div wrapper = new Div(about);
        wrapper.addClassName("provider-profile-card-about-wrapper");
        return wrapper;
    }

    private Span createKindBadge(ProviderProfileDto profile) {
        Span badge = new Span(getValue(forProviderKind(profile.getKind())));
        badge.addClassName("provider-profile-kind-badge");
        badge.addClassName("provider-profile-kind-badge--" + profile.getKind().name().toLowerCase());
        return badge;
    }

    private Div createCategoriesLine(ProviderProfileDto profile) {
        if (profile.getCategoryNames() == null || profile.getCategoryNames().isEmpty()) return null;
        return chipRow(PROVIDERS_CARD_CATEGORIES, profile.getCategoryNames(), "provider-profile-category-chip");
    }

    private Div createCityLine(ProviderProfileDto profile) {
        if (profile.getCityName() == null) return null;
        return chipRow(PROVIDERS_CARD_CITY, List.of(profile.getCityName()), "provider-profile-city-chip");
    }

    private Div chipRow(I18nKey label, List<String> names, String chipCssClass) {
        Div row = new Div();
        row.addClassName("provider-profile-card-chip-row");
        row.getElement().setAttribute("role", "list");
        row.getElement().setAttribute("aria-label", getValue(label));
        Span labelSpan = new Span(getValue(label));
        labelSpan.addClassName("overlay-chips-label");
        row.add(labelSpan);
        names.forEach(name -> {
            Span chip = new Span(name);
            chip.addClassName(chipCssClass);
            chip.getElement().setAttribute("role", "listitem");
            row.add(chip);
        });
        return row;
    }

    private HorizontalLayout createActions(ProviderProfileDto profile, Runnable onListChanged) {
        Button delete = createDeleteButton(profile, onListChanged);
        Button share  = createShareButton(profile);

        HorizontalLayout actions = new HorizontalLayout(delete, share);
        actions.addClassName("provider-profile-card-actions");
        return actions;
    }

    private Button createShareButton(ProviderProfileDto profile) {
        return new ShareActionButton(getValue(PROVIDERS_CARD_BUTTON_SHARE),
                () -> ShareUtil.share(this, appLinkService.providerProfileUrl(profile.getId()),
                        HtmlExcerptUtil.plainText(profile.getAbout()), () -> notificationService.success(PROVIDERS_CARD_NOTIFICATION_LINK_COPIED)),
                "provider-profile-share", true);
    }

    private Button createDeleteButton(ProviderProfileDto profile, Runnable onListChanged) {
        Button delete = new DeleteActionButton(getValue(PROVIDERS_CATALOG_OVERLAY_DELETE),
                () -> confirmAndDelete(profile, onListChanged), "provider-profile-delete", true);
        delete.setVisible(access.canEditUserAccount(profile.getActorId()));
        return delete;
    }

    private void confirmAndDelete(ProviderProfileDto profile, Runnable onListChanged) {
        ProviderProfileDeleteUtil.confirmAndDelete(i18nService, notificationService, providerProfileSaveService, access, profile, onListChanged);
    }
}
