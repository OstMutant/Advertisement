package org.ost.marketplace.ui.views.main.tabs.advertisements;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.platform.user.dto.UserSettings;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;
import org.ost.marketplace.security.AccessEvaluator;
import org.ost.marketplace.services.AdvertisementService;
import org.ost.platform.core.i18n.I18nService;
import org.ost.marketplace.ui.views.components.EmptyStateView;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.ui.query.QueryBlock;
import org.ost.ui.query.QueryStatusBar;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.AdvertisementOverlay;
import org.ost.marketplace.ui.views.services.pagination.SettingsPaginationBinding;
import org.ost.platform.core.ComponentFactory;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.ost.marketplace.common.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementsView extends VerticalLayout {

    private final transient AdvertisementService                  advertisementService;
    private final transient AdvertisementOverlay                  overlay;
    private final transient ComponentFactory<UiPrimaryButton>     primaryButtonFactory;
    private final transient ComponentFactory<AdvertisementCardView> cardViewFactory;
    private final transient ComponentFactory<EmptyStateView>      emptyStateFactory;
    private final transient I18nService                           i18n;
    private final transient AccessEvaluator                       access;

    private final QueryStatusBar<AdvertisementFilterDto> queryStatusBar;
    private final PaginationBar                          paginationBar;
    private final transient SettingsPaginationBinding    settingsPaginationBinding;

    private FlexLayout advertisementContainer;

    @PostConstruct
    public void init() {
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

        Shortcuts.addShortcutListener(this, () -> {
            if (access.isLoggedIn() && isVisible()) {
                overlay.openForCreate(this::refresh);
            }
        }, Key.KEY_N);

        settingsPaginationBinding.register(paginationBar, UserSettings::getAdsPageSize, this::refresh);
        refresh();
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
        UiPrimaryButton button = primaryButtonFactory.build(
                UiPrimaryButton.Parameters.builder()
                        .labelKey(ADVERTISEMENT_SIDEBAR_BUTTON_ADD)
                        .icon(VaadinIcon.PLUS.create())
                        .build());
        button.addClassName("add-advertisement-button");
        button.addClickListener(_ -> overlay.openForCreate(this::refresh));
        button.setVisible(access.isLoggedIn());
        return button;
    }

    private void refresh() {
        QueryBlock<AdvertisementFilterDto> queryBlock = queryStatusBar.getQueryBlock();
        AdvertisementFilterDto filter = queryBlock.getFilterProcessor().getOriginalFilter();
        Sort sort = queryBlock.getSortProcessor().getOriginalSort().getSort();

        List<AdvertisementInfoDto> ads = advertisementService.getFiltered(
                filter, paginationBar.getCurrentPage(), paginationBar.getPageSize(), sort);

        paginationBar.setTotalCount(advertisementService.count(filter));

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

        queryStatusBar.update();
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
