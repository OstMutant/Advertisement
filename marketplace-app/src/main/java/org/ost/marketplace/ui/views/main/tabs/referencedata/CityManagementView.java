package org.ost.marketplace.ui.views.main.tabs.referencedata;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.CityOverlay;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;

import java.util.List;
import java.util.Map;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@Slf4j
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class CityManagementView extends Div {

    private final TaxonCatalogService                      taxonCatalogService;
    private final I18nService                              i18n;
    private final NotificationService                      notificationService;
    private final AccessEvaluator                          access;
    private final CityOverlay                              overlay;

    private Div listContainer;

    @PostConstruct
    protected void init() {
        addClassName("city-management-view");

        listContainer = new Div();
        listContainer.addClassName("taxon-list-container");

        add(listContainer, overlay);
        refresh();
    }

    private void refresh() {
        try {
            listContainer.removeAll();

            UiPrimaryButton addBtn = new UiPrimaryButton(i18n.get(REFERENCE_DATA_BUTTON_ADD), VaadinIcon.PLUS.create());
            addBtn.addClassName("city-add-button");
            addBtn.addClickListener(_ -> overlay.openForCreate(this::refresh));
            listContainer.add(addBtn);

            List<TaxonDto> all     = taxonCatalogService.listAllByType(TaxonType.CITY, java.util.Locale.ENGLISH, true);
            Map<Long, Long> counts = all.isEmpty() ? Map.of() : taxonCatalogService.getUsageCounts(TaxonType.CITY);

            List<TaxonDto> active  = all.stream().filter(t -> !t.isDeleted()).toList();
            List<TaxonDto> deleted = all.stream().filter(TaxonDto::isDeleted).toList();

            if (active.isEmpty() && deleted.isEmpty()) {
                Span empty = new Span(i18n.get(CITY_VIEW_EMPTY));
                empty.addClassName("taxon-empty-state");
                listContainer.add(empty);
                return;
            }

            active.forEach(t  -> listContainer.add(buildRow(t, counts.getOrDefault(t.getId(), 0L), false)));
            deleted.forEach(t -> listContainer.add(buildRow(t, counts.getOrDefault(t.getId(), 0L), true)));
        } catch (Exception ex) {
            log.error("Failed to refresh city list", ex);
            notificationService.error(CITY_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage());
            listContainer.removeAll();
        }
    }

    private Div buildRow(TaxonDto city, long usageCount, boolean isDeleted) {
        Span nameSpan = new Span(city.getName());
        nameSpan.addClassName("taxon-row-name");
        if (isDeleted) nameSpan.addClassName("taxon-row-deleted");

        Span deletedBadge = new Span(i18n.get(CITY_VIEW_DELETED_LABEL));
        deletedBadge.addClassName("taxon-deleted-badge");
        deletedBadge.setVisible(isDeleted);

        Span countSpan = new Span(i18n.get(REFERENCE_DATA_USAGE_COUNT, usageCount));
        countSpan.addClassName("taxon-row-count");

        Div actions = buildRowActions(city, isDeleted);

        HorizontalLayout row = new HorizontalLayout(nameSpan, deletedBadge, countSpan, actions);
        row.addClassName("taxon-row");
        row.setAlignItems(HorizontalLayout.Alignment.CENTER);

        if (!isDeleted) {
            nameSpan.addClickListener(_ -> overlay.openForView(city, this::updateRowInPlace));
        }

        Div wrapper = new Div(row);
        wrapper.addClassName("taxon-row-wrapper");
        wrapper.getElement().setAttribute("data-taxon-id", String.valueOf(city.getId()));

        return wrapper;
    }

    private void updateRowInPlace(TaxonDto fresh) {
        String id = String.valueOf(fresh.getId());
        listContainer.getChildren()
                .filter(c -> id.equals(c.getElement().getAttribute("data-taxon-id")))
                .findFirst()
                .ifPresent(old -> {
                    int index = listContainer.indexOf(old);
                    long usageCount = taxonCatalogService.getUsageCounts(TaxonType.CITY).getOrDefault(fresh.getId(), 0L);
                    Div updated = buildRow(fresh, usageCount, fresh.isDeleted());
                    listContainer.remove(old);
                    listContainer.addComponentAtIndex(index, updated);
                });
    }

    private Div buildRowActions(TaxonDto city, boolean isDeleted) {
        Div actions = new Div();
        actions.addClassName("taxon-row-actions");

        if (isDeleted) {
            UiIconButton restoreBtn = new UiIconButton(i18n.get(CITY_VIEW_TOOLTIP_RESTORE), VaadinIcon.ARROW_BACKWARD.create());
            restoreBtn.addClickListener(e -> doRestore(city));
            actions.add(restoreBtn);
        } else {
            UiIconButton editBtn = new UiIconButton(i18n.get(CITY_VIEW_TOOLTIP_EDIT), VaadinIcon.PENCIL.create());
            editBtn.addClickListener(e -> overlay.openForEdit(city, this::updateRowInPlace));

            UiIconButton deleteBtn = new UiIconButton(i18n.get(CITY_VIEW_TOOLTIP_DELETE), VaadinIcon.TRASH.create());
            deleteBtn.addClickListener(e -> confirmAndDelete(city));

            actions.add(editBtn, deleteBtn);
        }
        return actions;
    }

    private void confirmAndDelete(TaxonDto city) {
        new ConfirmActionDialog(
                i18n.get(CITY_VIEW_CONFIRM_DELETE_TITLE),
                i18n.get(CITY_VIEW_CONFIRM_DELETE_TEXT, city.getName()),
                i18n.get(CITY_VIEW_CONFIRM_DELETE_BUTTON),
                i18n.get(CITY_VIEW_CONFIRM_CANCEL_BUTTON),
                () -> {
                    try {
                        taxonCatalogService.softDelete(city.getId(), access.getCurrentUserId(), city.getVersion());
                        notificationService.success(CITY_VIEW_NOTIFICATION_DELETED);
                        refresh();
                    } catch (AccessDeniedException e) {
                        log.warn("Access denied deleting city id={}: {}", city.getId(), e.getMessage());
                        notificationService.accessDenied();
                    } catch (@SuppressWarnings("java:S7467") Exception e) {
                        log.error("Error deleting city id={}", city.getId(), e);
                        notificationService.error(CITY_VIEW_NOTIFICATION_DELETE_ERROR, e.getMessage());
                    }
                }
        ).open();
    }

    private void doRestore(TaxonDto city) {
        try {
            taxonCatalogService.restore(city.getId(), access.getCurrentUserId());
            notificationService.success(CITY_VIEW_NOTIFICATION_RESTORED);
            refresh();
        } catch (AccessDeniedException e) {
            log.warn("Access denied restoring city id={}: {}", city.getId(), e.getMessage());
            notificationService.accessDenied();
        } catch (Exception e) {
            log.error("Error restoring city id={}", city.getId(), e);
            notificationService.error(CITY_VIEW_NOTIFICATION_DELETE_ERROR, e.getMessage());
        }
    }
}
