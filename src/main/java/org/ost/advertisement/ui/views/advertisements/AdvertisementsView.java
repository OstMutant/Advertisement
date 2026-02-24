package org.ost.advertisement.ui.views.advertisements;

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
import org.ost.advertisement.ui.views.advertisements.overlay.AdvertisementOverlay;
import org.ost.advertisement.ui.views.advertisements.query.elements.AdvertisementQueryBlock;
import org.ost.advertisement.ui.views.advertisements.query.elements.AdvertisementQueryStatusBar;
import org.ost.advertisement.ui.views.components.EmptyStateView;
import org.ost.advertisement.ui.views.components.PaginationBarModern;
import org.ost.advertisement.ui.views.components.query.filter.processor.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.sort.processor.SortProcessor;

import java.util.List;

import static com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.START;
import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementsView extends VerticalLayout {

    private final transient AdvertisementService advertisementService;
    private final transient AdvertisementOverlay overlay;
    private final transient AdvertisementCardView.Builder cardBuilder;
    private final transient I18nService i18n;
    private final transient AccessEvaluator access;

    private final AdvertisementQueryStatusBar queryStatusBar;
    private final FlexLayout advertisementContainer = new FlexLayout();
    private final PaginationBarModern paginationBar;
    private final transient EmptyStateView.Builder emptyStateBuilder;
    private final Button addButton = new Button();

    @PostConstruct
    public void init() {

        initAdvertisementContainer();
        initQueryBar();
        initPagination();
        initAddButton();

        addClassName("advertisements-view");

        add(queryStatusBar, queryStatusBar.getQueryBlock(), addButton, advertisementContainer, paginationBar, overlay);

        setFlexGrow(1, advertisementContainer);
        setSizeFull();

        refreshAdvertisements();
    }

    private void initAddButton() {
        addButton.setText(i18n.get(ADVERTISEMENT_SIDEBAR_BUTTON_ADD));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClassName("add-advertisement-button");
        addButton.addClickListener(_ -> overlay.openForCreate(this::refreshAdvertisements));
        addButton.setVisible(access.isLoggedIn());
    }

    private void initAdvertisementContainer() {
        advertisementContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        advertisementContainer.setJustifyContentMode(START);
        advertisementContainer.setAlignItems(Alignment.STRETCH);
        advertisementContainer.addClassName("advertisement-container");
    }

    private void initQueryBar() {
        queryStatusBar.getQueryBlock().addEventListener(() -> {
            paginationBar.setTotalCount(0);
            refreshAdvertisements();
        });
    }

    private void initPagination() {
        paginationBar.setPageChangeListener(_ -> refreshAdvertisements());
    }

    private void refreshAdvertisements() {
        AdvertisementQueryBlock queryBlock = queryStatusBar.getQueryBlock();
        FilterProcessor<AdvertisementFilterDto> filterProcessor = queryBlock.getFilterProcessor();
        SortProcessor sortProcessor = queryBlock.getSortProcessor();

        int page = paginationBar.getCurrentPage();
        int size = paginationBar.getPageSize();

        AdvertisementFilterDto filter = filterProcessor.getOriginalFilter();

        List<AdvertisementInfoDto> ads = advertisementService.getFiltered(
                filter, page, size, sortProcessor.getOriginalSort().getSort());

        paginationBar.setTotalCount(advertisementService.count(filter));

        advertisementContainer.removeAll();
        if (ads.isEmpty()) {
            advertisementContainer.add(createEmptyState());
        } else {
            ads.forEach(ad -> advertisementContainer.add(cardBuilder.build(ad, this::refreshAdvertisements)));
        }

        queryStatusBar.update();
    }

    private VerticalLayout createEmptyState() {
        return emptyStateBuilder.build(EmptyStateView.Parameters.builder()
                .icon(VaadinIcon.CLIPBOARD_TEXT)
                .title(i18n.get(ADVERTISEMENT_EMPTY_TITLE))
                .hint(i18n.get(ADVERTISEMENT_EMPTY_HINT))
                .build());
    }
}