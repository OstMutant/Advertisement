package org.ost.advertisement.ui.views.tabs.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.tabs.advertisements.overlay.AdvertisementOverlay;
import org.ost.advertisement.ui.views.components.EmptyStateView;
import org.ost.advertisement.ui.views.components.PaginationBarModern;
import org.ost.advertisement.ui.views.components.query.QueryBlock;
import org.ost.advertisement.ui.views.components.query.QueryStatusBar;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementsView extends VerticalLayout {

    private final transient AdvertisementService        advertisementService;
    private final transient AdvertisementOverlay        overlay;
    private final transient AdvertisementCardView.Builder cardBuilder;
    private final transient I18nService                 i18n;
    private final transient AccessEvaluator             access;
    private final transient EmptyStateView.Builder      emptyStateBuilder;

    private final QueryStatusBar<AdvertisementFilterDto> queryStatusBar;
    private final PaginationBarModern         paginationBar;

    private FlexLayout advertisementContainer;

    @PostConstruct
    public void init() {
        advertisementContainer = buildAdvertisementContainer();
        Button addButton = buildAddButton();

        addClassName("advertisements-view");
        setSizeFull();
        setFlexGrow(1, advertisementContainer);

        add(queryStatusBar, queryStatusBar.getQueryBlock(), addButton, advertisementContainer, paginationBar, overlay);

        queryStatusBar.getQueryBlock().addEventListener(() -> {
            paginationBar.setTotalCount(0);
            refresh();
        });

        paginationBar.setPageChangeListener(_ -> refresh());

        refresh();
    }

    private FlexLayout buildAdvertisementContainer() {
        FlexLayout container = new FlexLayout();
        container.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        container.setJustifyContentMode(JustifyContentMode.START);
        container.setAlignItems(Alignment.STRETCH);
        container.addClassName("advertisement-container");
        return container;
    }

    private Button buildAddButton() {
        Button button = new Button(i18n.get(ADVERTISEMENT_SIDEBAR_BUTTON_ADD));
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.addClassName("add-advertisement-button");
        button.addClickListener(_ -> overlay.openForCreate(this::refresh));
        button.setVisible(access.isLoggedIn());
        return button;
    }

    private void refresh() {
        QueryBlock<AdvertisementFilterDto> queryBlock = queryStatusBar.getQueryBlock();
        AdvertisementFilterDto    filter     = queryBlock.getFilterProcessor().getOriginalFilter();
        Sort                      sort       = queryBlock.getSortProcessor().getOriginalSort().getSort();

        List<AdvertisementInfoDto> ads = advertisementService.getFiltered(filter, paginationBar.getCurrentPage(), paginationBar.getPageSize(), sort);

        paginationBar.setTotalCount(advertisementService.count(filter));

        advertisementContainer.removeAll();

        if (ads.isEmpty()) {
            advertisementContainer.add(buildEmptyState());
        } else {
            ads.stream()
                    .map(ad -> cardBuilder.build(
                            AdvertisementCardView.Parameters.builder()
                                    .ad(ad)
                                    .onChanged(this::refresh)
                                    .build()))
                    .forEach(advertisementContainer::add);
        }

        queryStatusBar.update();
    }

    private EmptyStateView buildEmptyState() {
        return emptyStateBuilder.build(EmptyStateView.Parameters.builder()
                .icon(VaadinIcon.CLIPBOARD_TEXT)
                .title(i18n.get(ADVERTISEMENT_EMPTY_TITLE))
                .hint(i18n.get(ADVERTISEMENT_EMPTY_HINT))
                .build());
    }
}