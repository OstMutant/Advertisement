package org.ost.marketplace.ui.views.main.tabs.advertisements;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.platform.core.ComponentFactory;
import org.ost.marketplace.ui.views.components.EmptyStateView;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.query.QueryStatusBar;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.AdvertisementOverlay;
import org.ost.marketplace.ui.views.services.pagination.SettingsPaginationBinding;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@Slf4j
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementsView extends VerticalLayout {

    private final transient ComponentFactory<AdvertisementPort>         advertisementPortFactory;
    private final transient AdvertisementOverlay                      overlay;
    private final transient UiComponentFactory<AdvertisementCardView>   cardViewFactory;
    private final transient UiComponentFactory<EmptyStateView>          emptyStateFactory;
    private final transient I18nService                               i18n;
    private final transient AccessEvaluator                           access;
    private final transient LocaleProvider                            localeProvider;
    private final transient NotificationService                       notificationService;

    private final QueryStatusBar<AdvertisementFilterDto> queryStatusBar;
    private final PaginationBar                          paginationBar;
    private final transient SettingsPaginationBinding    settingsPaginationBinding;

    private FlexLayout advertisementContainer;

    @PostConstruct
    protected void init() {
        advertisementContainer = buildAdvertisementContainer();
        UiPrimaryButton addButton = buildAddButton();

        VerticalLayout contentWrapper = new VerticalLayout(
                queryStatusBar, queryStatusBar.getQueryBlock(), addButton, advertisementContainer, paginationBar
        );
        contentWrapper.addClassName("advertisements-content-wrapper");
        contentWrapper.setPadding(false);
        contentWrapper.setSpacing(false);
        contentWrapper.setWidthFull();
        contentWrapper.setFlexGrow(1, advertisementContainer);

        addClassName("advertisements-view");
        setSizeFull();
        setFlexGrow(1, contentWrapper);

        add(contentWrapper, overlay);

        queryStatusBar.getQueryBlock().addEventListener(() -> {
            paginationBar.setTotalCount(0);
            refresh();
        });

        paginationBar.setPageChangeListener(_ -> refresh());

        // Vaadin's Shortcuts API uses event.target which is retargeted in shadow DOM —
        // the host element replaces the inner <input>, bypassing the "don't fire in inputs" guard.
        // composedPath() traverses into shadow roots and correctly detects focused input elements.
        getElement().executeJs("""
                this.addEventListener('keydown', (e) => {
                    if (e.key.toLowerCase() !== 'n' || e.ctrlKey || e.metaKey || e.altKey) return;
                    const path = e.composedPath ? e.composedPath() : [];
                    const inInput = path.some(el => {
                        const tag = el.localName;
                        return tag === 'input' || tag === 'textarea' || el.isContentEditable === true;
                    });
                    if (!inInput) {
                        e.preventDefault();
                        this.$server.onNewAdvertisementShortcut();
                    }
                });
                """);

        settingsPaginationBinding.register(paginationBar, UserSettingsDto::getAdsPageSize, this::refresh);
        refresh();
    }

    @ClientCallable
    public void onNewAdvertisementShortcut() {
        if (access.isLoggedIn() && isVisible()) {
            overlay.openForCreate(this::refresh);
        }
    }

    @PreDestroy
    public void destroy() {
        settingsPaginationBinding.unregister();
    }

    private FlexLayout buildAdvertisementContainer() {
        FlexLayout container = new FlexLayout();
        container.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        container.setJustifyContentMode(JustifyContentMode.START);
        container.setAlignItems(Alignment.STRETCH);
        container.addClassName("advertisement-container");
        return container;
    }

    private UiPrimaryButton buildAddButton() {
        UiPrimaryButton button = new UiPrimaryButton(i18n.get(ADVERTISEMENT_SIDEBAR_BUTTON_ADD), VaadinIcon.PLUS.create());
        button.addClassName("add-advertisement-button");
        button.addClickListener(_ -> overlay.openForCreate(this::refresh));
        button.setVisible(access.isLoggedIn());
        return button;
    }

    private void refresh() {
        QueryBlock<AdvertisementFilterDto> queryBlock = queryStatusBar.getQueryBlock();
        AdvertisementFilterDto filter = queryBlock.getFilterProcessor().getOriginalFilter();
        Sort sort = queryBlock.getSortProcessor().getOriginalSort().getSort();

        try {
            List<AdvertisementInfoDto> ads = advertisementPortFactory.findIfAvailable()
                    .map(p -> p.getFiltered(filter, paginationBar.getCurrentPage(), paginationBar.getPageSize(), sort, localeProvider.getCurrentLocale()))
                    .orElse(List.of());
            int total = advertisementPortFactory.findIfAvailable()
                    .map(p -> p.count(filter))
                    .orElse(0);
            paginationBar.setTotalCount(total);
            advertisementContainer.removeAll();
            if (ads.isEmpty()) {
                advertisementContainer.add(buildEmptyState());
            } else {
                ads.stream()
                        .map(ad -> cardViewFactory.build(
                                AdvertisementCardView.Parameters.builder()
                                        .ad(ad)
                                        .onChanged(this::refresh)
                                        .build()))
                        .forEach(advertisementContainer::add);
            }
        } catch (Exception ex) {
            log.error("Failed to refresh advertisements", ex);
            notificationService.error(ADVERTISEMENT_VIEW_NOTIFICATION_REFRESH_ERROR);
            advertisementContainer.removeAll();
            paginationBar.setTotalCount(0);
        } finally {
            queryStatusBar.update();
        }
    }

    private EmptyStateView buildEmptyState() {
        return emptyStateFactory.build(
                EmptyStateView.Parameters.builder()
                        .icon(VaadinIcon.CLIPBOARD_TEXT)
                        .title(i18n.get(ADVERTISEMENT_EMPTY_TITLE))
                        .hint(i18n.get(ADVERTISEMENT_EMPTY_HINT))
                        .build());
    }
}
