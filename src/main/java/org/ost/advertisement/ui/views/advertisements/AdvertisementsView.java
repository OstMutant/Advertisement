package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.overlay.AdvertisementOverlay;
import org.ost.advertisement.ui.views.advertisements.query.elements.AdvertisementQueryBlock;
import org.ost.advertisement.ui.views.advertisements.query.elements.AdvertisementQueryStatusBar;
import org.ost.advertisement.ui.views.components.PaginationBarModern;
import org.ost.advertisement.ui.views.components.query.filter.processor.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.sort.processor.SortProcessor;

import java.util.List;

import static com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.START;
import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
public class AdvertisementsView extends VerticalLayout {

    private final transient AdvertisementService      advertisementService;
    private final transient AdvertisementOverlay      overlay;
    private final transient AdvertisementCardView.Builder cardBuilder;
    private final transient I18nService               i18n;
    private final transient AccessEvaluator           access;

    private final AdvertisementQueryStatusBar queryStatusBar;
    private final FlexLayout                 advertisementContainer;
    private final PaginationBarModern        paginationBar;

    public AdvertisementsView(AdvertisementService advertisementService,
                              AdvertisementQueryStatusBar queryStatusBar,
                              AdvertisementOverlay overlay,
                              AdvertisementCardView.Builder cardBuilder,
                              I18nService i18n,
                              AccessEvaluator access) {
        this.advertisementService   = advertisementService;
        this.queryStatusBar         = queryStatusBar;
        this.overlay                = overlay;
        this.cardBuilder            = cardBuilder;
        this.i18n                   = i18n;
        this.access                 = access;
        this.paginationBar          = new PaginationBarModern(i18n);
        this.advertisementContainer = createAdvertisementContainer();

        initQueryBar();
        initPagination();

        addClassName("advertisements-view");

        // overlay is position:fixed — does not affect layout flow
        add(queryStatusBar, queryStatusBar.getQueryBlock(), createAddButton(),
                advertisementContainer, paginationBar, overlay);

        setFlexGrow(1, advertisementContainer);
        setSizeFull();

        refreshAdvertisements();
    }

    private Button createAddButton() {
        Button button = new Button(i18n.get(ADVERTISEMENT_SIDEBAR_BUTTON_ADD));
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.addClassName("add-advertisement-button");
        button.addClickListener(_ -> overlay.openForCreate(this::refreshAdvertisements));
        button.setVisible(access.isLoggedIn());
        return button;
    }

    private FlexLayout createAdvertisementContainer() {
        FlexLayout container = new FlexLayout();
        container.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        container.setJustifyContentMode(START);
        container.setAlignItems(Alignment.STRETCH);
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

        List<AdvertisementInfoDto> ads = advertisementService.getFiltered(
                filter, page, size, sortProcessor.getOriginalSort().getSort());

        paginationBar.setTotalCount(advertisementService.count(filter));

        advertisementContainer.removeAll();
        if (ads.isEmpty()) {
            advertisementContainer.add(createEmptyState());
        } else {
            // pass this::refreshAdvertisements so overlay can trigger a list refresh after save/delete
            ads.forEach(ad -> advertisementContainer.add(
                    cardBuilder.build(ad, overlay, this::refreshAdvertisements)));
        }

        queryStatusBar.update();
    }

    private VerticalLayout createEmptyState() {
        com.vaadin.flow.component.icon.Icon icon = VaadinIcon.CLIPBOARD_TEXT.create();
        icon.addClassName("empty-state-icon");

        Span title = new Span(i18n.get(ADVERTISEMENT_EMPTY_TITLE));
        title.addClassName("empty-state-title");

        Span hint = new Span(i18n.get(ADVERTISEMENT_EMPTY_HINT));
        hint.addClassName("empty-state-hint");

        VerticalLayout emptyState = new VerticalLayout(icon, title, hint);
        emptyState.addClassName("empty-state");
        emptyState.setAlignItems(Alignment.CENTER);
        return emptyState;
    }
}