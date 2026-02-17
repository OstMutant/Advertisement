package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.dialogs.AdvertisementUpsertDialog;
import org.ost.advertisement.ui.views.advertisements.query.elements.AdvertisementQueryBlock;
import org.ost.advertisement.ui.views.advertisements.query.elements.AdvertisementQueryStatusBar;
import org.ost.advertisement.ui.views.components.PaginationBarModern;
import org.ost.advertisement.ui.views.components.query.filter.processor.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.sort.processor.SortProcessor;

import java.util.List;

import static com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.START;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SIDEBAR_BUTTON_ADD;

@SpringComponent
@UIScope
public class AdvertisementsView extends VerticalLayout {

    private final transient AdvertisementService advertisementService;
    private final AdvertisementQueryStatusBar queryStatusBar;
    private final transient AdvertisementUpsertDialog.Builder upsertDialogBuilder;
    private final transient AdvertisementCardView.Builder cardBuilder;
    private final FlexLayout advertisementContainer;
    private final PaginationBarModern paginationBar;

    public AdvertisementsView(AdvertisementService advertisementService,
                              AdvertisementQueryStatusBar queryStatusBar,
                              AdvertisementUpsertDialog.Builder upsertDialogBuilder,
                              I18nService i18n,
                              AdvertisementCardView.Builder cardBuilder) {
        this.advertisementService = advertisementService;
        this.queryStatusBar = queryStatusBar;
        this.upsertDialogBuilder = upsertDialogBuilder;
        this.cardBuilder = cardBuilder;
        this.paginationBar = new PaginationBarModern(i18n);
        this.advertisementContainer = createAdvertisementContainer();

        Button addAdvertisementButton = createAddButton(i18n);

        initQueryBar();
        initPagination();

        addClassName("advertisements-view");

        add(queryStatusBar, queryStatusBar.getQueryBlock(), addAdvertisementButton, advertisementContainer, paginationBar);
        setFlexGrow(1, advertisementContainer);

        setSizeFull();

        refreshAdvertisements();
    }

    private Button createAddButton(I18nService i18n) {
        Button button = new Button(i18n.get(ADVERTISEMENT_SIDEBAR_BUTTON_ADD));
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.addClassName("add-advertisement-button");
        button.addClickListener(_ -> upsertDialogBuilder.buildAndOpen(this::refreshAdvertisements));
        return button;
    }

    private FlexLayout createAdvertisementContainer() {
        FlexLayout container = new FlexLayout();
        container.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        container.setJustifyContentMode(START);
        container.setAlignItems(Alignment.START);
        container.addClassName("advertisement-container");
        return container;
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

        List<AdvertisementInfoDto> ads = advertisementService.getFiltered(filter, page, size, sortProcessor.getOriginalSort().getSort());

        paginationBar.setTotalCount(advertisementService.count(filter));

        advertisementContainer.removeAll();
        ads.forEach(ad -> advertisementContainer.add(cardBuilder.build(ad, this::refreshAdvertisements)));

        queryStatusBar.update();
    }
}
