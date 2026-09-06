package org.ost.marketplace.ui.views.main.tabs.providers;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.query.QueryStatusBar;
import org.ost.marketplace.ui.views.components.EmptyStateView;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.marketplace.ui.views.main.tabs.providers.overlay.ProviderProfileCatalogOverlay;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.services.pagination.SettingsPaginationBinding;
import org.ost.marketplace.ui.views.utils.ValidationErrorUtil;
import org.ost.orchestrator.services.ProviderProfileDisplayEnrichmentService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/** Public catalog listing of provider profiles -- visible to any visitor, no create/edit affordance here (that lives in {@code AccountOverlay}'s own Provider Profile tab). */
@Slf4j
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class ProvidersView extends VerticalLayout {

    private final transient ProviderProfileReadService              providerProfileReadService;
    private final transient ProviderProfileDisplayEnrichmentService enrichmentService;
    private final transient ProviderProfileCatalogOverlay           overlay;
    private final transient UiComponentFactory<ProviderProfileCardView> cardViewFactory;
    private final transient I18nService                             i18n;
    private final transient LocaleProvider                          localeProvider;
    private final transient NotificationService                     notificationService;

    private final QueryStatusBar<ProviderProfileFilterDto> queryStatusBar;
    private final PaginationBar                             paginationBar;
    private final transient SettingsPaginationBinding        settingsPaginationBinding;

    private FlexLayout providerProfileContainer;

    @PostConstruct
    protected void init() {
        providerProfileContainer = buildProviderProfileContainer();

        VerticalLayout contentWrapper = new VerticalLayout(
                queryStatusBar, queryStatusBar.getQueryBlock(), providerProfileContainer, paginationBar
        );
        contentWrapper.addClassName("providers-content-wrapper");
        contentWrapper.setPadding(false);
        contentWrapper.setSpacing(false);
        contentWrapper.setWidthFull();
        contentWrapper.setFlexGrow(1, providerProfileContainer);

        addClassName("providers-view");
        setSizeFull();
        setFlexGrow(1, contentWrapper);

        add(contentWrapper, overlay);

        queryStatusBar.getQueryBlock().addEventListener(() -> {
            paginationBar.setTotalCount(0);
            refresh();
        });

        paginationBar.setPageChangeListener(_ -> refresh());

        settingsPaginationBinding.register(paginationBar, UserSettingsDto::getAdsPageSize, this::refresh);
        refresh();
    }

    public boolean openPendingDeepLinkIfAny() {
        PendingProviderProfileDeepLink pending = VaadinSession.getCurrent().getAttribute(PendingProviderProfileDeepLink.class);
        if (pending == null) return false;
        VaadinSession.getCurrent().setAttribute(PendingProviderProfileDeepLink.class, null);
        return providerProfileReadService.findById(pending.providerId())
                .map(this::enrichSingle)
                .map(profile -> {
                    overlay.openForView(profile, this::refresh, () -> {});
                    return true;
                })
                .orElse(false);
    }

    @PreDestroy
    public void destroy() {
        settingsPaginationBinding.unregister();
    }

    private FlexLayout buildProviderProfileContainer() {
        FlexLayout container = new FlexLayout();
        container.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        container.setJustifyContentMode(JustifyContentMode.START);
        container.setAlignItems(Alignment.STRETCH);
        container.addClassName("provider-profile-container");
        return container;
    }

    private void refresh() {
        QueryBlock<ProviderProfileFilterDto> queryBlock = queryStatusBar.getQueryBlock();
        ProviderProfileFilterDto filter = queryBlock.getFilterProcessor().getOriginalFilter();
        Sort sort = queryBlock.getSortProcessor().getOriginalSort().getSort();

        try {
            List<ProviderProfileDto> fetched = providerProfileReadService.getFiltered(
                    filter, paginationBar.getCurrentPage(), paginationBar.getPageSize(), sort);
            List<ProviderProfileDto> profiles = fetched.isEmpty() ? fetched : enrich(fetched);
            int total = providerProfileReadService.count(filter);
            paginationBar.setTotalCount(total);
            providerProfileContainer.removeAll();
            if (profiles.isEmpty()) {
                providerProfileContainer.add(buildEmptyState());
            } else {
                profiles.stream()
                        .map(profile -> cardViewFactory.build(
                                ProviderProfileCardView.Parameters.builder()
                                        .profile(profile)
                                        .onListChanged(this::refresh)
                                        .onClosed(() -> {})
                                        .build()))
                        .forEach(providerProfileContainer::add);
            }
        } catch (ConstraintViolationException ex) {
            log.warn("Validation error while fetching provider profiles: {}", ex.getMessage(), ex);
            notificationService.error(i18n.get(PROVIDERS_VIEW_NOTIFICATION_VALIDATION_FAILED) + "\n" + ValidationErrorUtil.buildMessage(ex));
            providerProfileContainer.removeAll();
            paginationBar.setTotalCount(0);
        } catch (Exception ex) {
            log.error("Failed to refresh providers", ex);
            notificationService.error(PROVIDERS_VIEW_NOTIFICATION_REFRESH_ERROR);
            providerProfileContainer.removeAll();
            paginationBar.setTotalCount(0);
        } finally {
            queryStatusBar.update();
        }
    }

    private List<ProviderProfileDto> enrich(List<ProviderProfileDto> profiles) {
        profiles = enrichmentService.enrichWithCategoriesAndCity(profiles, localeProvider.getCurrentLocale());
        return enrichmentService.enrichWithActorInfo(profiles);
    }

    private ProviderProfileDto enrichSingle(ProviderProfileDto profile) {
        ProviderProfileDto enriched = enrichmentService.enrichWithCategoryAndCity(profile, localeProvider.getCurrentLocale());
        return enrichmentService.enrichWithActor(enriched);
    }

    private EmptyStateView buildEmptyState() {
        return new EmptyStateView(VaadinIcon.BRIEFCASE,
                i18n.get(PROVIDERS_EMPTY_TITLE), i18n.get(PROVIDERS_EMPTY_HINT));
    }
}
