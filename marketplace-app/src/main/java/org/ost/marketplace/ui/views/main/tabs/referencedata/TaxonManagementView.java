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
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.TaxonOverlay;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.spi.TaxonPort;

import java.util.List;
import java.util.Map;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@Slf4j
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class TaxonManagementView extends Div {

    private final ComponentFactory<TaxonPort>              taxonPortFactory;
    private final I18nService                              i18n;
    private final NotificationService                      notificationService;
    private final AccessEvaluator                          access;
    private final TaxonOverlay                             overlay;

    private Div listContainer;

    @PostConstruct
    protected void init() {
        addClassName("taxon-management-view");

        listContainer = new Div();
        listContainer.addClassName("taxon-list-container");

        add(listContainer, overlay);
        refresh();
    }

    private void refresh() {
        try {
            listContainer.removeAll();

            UiPrimaryButton addBtn = new UiPrimaryButton(i18n.get(REFERENCE_DATA_BUTTON_ADD), VaadinIcon.PLUS.create());
            addBtn.addClassName("taxon-add-button");
            addBtn.addClickListener(_ -> overlay.openForCreate(this::refresh));
            listContainer.add(addBtn);

            taxonPortFactory.ifAvailable(port -> {
                List<TaxonDto> all     = port.listAllByType(TaxonType.CATEGORY, java.util.Locale.ENGLISH, true);
                Map<Long, Long> counts = all.isEmpty() ? Map.of() : port.getUsageCounts(TaxonType.CATEGORY);

                List<TaxonDto> active  = all.stream().filter(t -> !t.isDeleted()).toList();
                List<TaxonDto> deleted = all.stream().filter(TaxonDto::isDeleted).toList();

                if (active.isEmpty() && deleted.isEmpty()) {
                    Span empty = new Span(i18n.get(TAXON_VIEW_EMPTY));
                    empty.addClassName("taxon-empty-state");
                    listContainer.add(empty);
                    return;
                }

                active.forEach(t  -> listContainer.add(buildRow(t, counts.getOrDefault(t.getId(), 0L), false)));
                deleted.forEach(t -> listContainer.add(buildRow(t, counts.getOrDefault(t.getId(), 0L), true)));
            });
        } catch (Exception ex) {
            log.error("Failed to refresh taxon list", ex);
            notificationService.error(TAXON_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage());
            listContainer.removeAll();
        }
    }

    private Div buildRow(TaxonDto taxon, long usageCount, boolean isDeleted) {
        Span nameSpan = new Span(taxon.getName());
        nameSpan.addClassName("taxon-row-name");
        if (isDeleted) nameSpan.addClassName("taxon-row-deleted");

        Span deletedBadge = new Span(i18n.get(TAXON_VIEW_DELETED_LABEL));
        deletedBadge.addClassName("taxon-deleted-badge");
        deletedBadge.setVisible(isDeleted);

        Span countSpan = new Span(i18n.get(REFERENCE_DATA_USAGE_COUNT, usageCount));
        countSpan.addClassName("taxon-row-count");

        Div actions = buildRowActions(taxon, isDeleted);

        HorizontalLayout row = new HorizontalLayout(nameSpan, deletedBadge, countSpan, actions);
        row.addClassName("taxon-row");
        row.setAlignItems(HorizontalLayout.Alignment.CENTER);

        if (!isDeleted) {
            nameSpan.addClickListener(_ -> overlay.openForView(taxon, this::refresh));
        }

        Div wrapper = new Div(row);
        wrapper.addClassName("taxon-row-wrapper");

        return wrapper;
    }

    private Div buildRowActions(TaxonDto taxon, boolean isDeleted) {
        Div actions = new Div();
        actions.addClassName("taxon-row-actions");

        if (isDeleted) {
            UiIconButton restoreBtn = new UiIconButton(i18n.get(TAXON_VIEW_TOOLTIP_RESTORE), VaadinIcon.ARROW_BACKWARD.create());
            restoreBtn.addClickListener(e -> doRestore(taxon));
            actions.add(restoreBtn);
        } else {
            UiIconButton editBtn = new UiIconButton(i18n.get(TAXON_VIEW_TOOLTIP_EDIT), VaadinIcon.PENCIL.create());
            editBtn.addClickListener(e -> overlay.openForEdit(taxon, this::refresh));

            UiIconButton deleteBtn = new UiIconButton(i18n.get(TAXON_VIEW_TOOLTIP_DELETE), VaadinIcon.TRASH.create());
            deleteBtn.addClickListener(e -> confirmAndDelete(taxon));

            actions.add(editBtn, deleteBtn);
        }
        return actions;
    }

    private void confirmAndDelete(TaxonDto taxon) {
        new ConfirmActionDialog(
                i18n.get(TAXON_VIEW_CONFIRM_DELETE_TITLE),
                i18n.get(TAXON_VIEW_CONFIRM_DELETE_TEXT, taxon.getName()),
                i18n.get(TAXON_VIEW_CONFIRM_DELETE_BUTTON),
                i18n.get(TAXON_VIEW_CONFIRM_CANCEL_BUTTON),
                () -> {
                    try {
                        taxonPortFactory.ifAvailable(p -> p.softDelete(taxon.getId(), access.getCurrentUserId(), taxon.getVersion()));
                        notificationService.success(TAXON_VIEW_NOTIFICATION_DELETED);
                        refresh();
                    } catch (Exception e) {
                        log.error("Error deleting taxon id={}", taxon.getId(), e);
                        notificationService.error(TAXON_VIEW_NOTIFICATION_DELETE_ERROR, e.getMessage());
                    }
                }
        ).open();
    }

    private void doRestore(TaxonDto taxon) {
        try {
            taxonPortFactory.ifAvailable(p -> p.restore(taxon.getId(), access.getCurrentUserId()));
            notificationService.success(TAXON_VIEW_NOTIFICATION_RESTORED);
            refresh();
        } catch (Exception e) {
            log.error("Error restoring taxon id={}", taxon.getId(), e);
            notificationService.error(TAXON_VIEW_NOTIFICATION_DELETE_ERROR, e.getMessage());
        }
    }
}
