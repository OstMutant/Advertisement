package org.ost.marketplace.ui.views.main.tabs.referencedata;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.TaxonManagementOverlay;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.ost.marketplace.services.i18n.I18nKey.REFERENCE_DATA_BUTTON_ADD;
import static org.ost.marketplace.services.i18n.I18nKey.REFERENCE_DATA_USAGE_COUNT;

/** Shared list/row/delete/restore logic for a {@link TaxonType}-backed management screen, parameterized by the concrete City/Category subclass's labels, CSS prefix, and overlay. */
@Slf4j
public abstract class AbstractTaxonManagementView extends Div {

    /** The domain-specific i18n keys this view needs -- everything else is shared across every {@link TaxonType}. */
    public record Labels(
            I18nKey empty, I18nKey deletedLabel,
            I18nKey tooltipRestore, I18nKey tooltipEdit, I18nKey tooltipDelete,
            I18nKey confirmDeleteTitle, I18nKey confirmDeleteText, I18nKey confirmDeleteButton, I18nKey confirmCancelButton,
            I18nKey notificationDeleteError, I18nKey notificationDeleted, I18nKey notificationRestored) {}

    protected abstract TaxonCatalogService  getTaxonCatalogService();
    protected abstract EntityOverlaySupport getSupport();
    protected abstract AccessEvaluator      getAccess();
    protected abstract <O extends Div & TaxonManagementOverlay> O getOverlay();
    protected abstract TaxonType            getTaxonType();
    protected abstract String               getCssPrefix();
    protected abstract Labels               getLabels();

    private Div listContainer;

    @PostConstruct
    protected void init() {
        addClassName(getCssPrefix() + "-management-view");

        listContainer = new Div();
        listContainer.addClassName("taxon-list-container");

        add(listContainer, getOverlay());
        refresh();
    }

    private void refresh() {
        try {
            listContainer.removeAll();

            UiPrimaryButton addBtn = new UiPrimaryButton(getSupport().getI18n().get(REFERENCE_DATA_BUTTON_ADD), VaadinIcon.PLUS.create());
            addBtn.addClassName(getCssPrefix() + "-add-button");
            addBtn.addClickListener(_ -> getOverlay().openForCreate(this::refresh));
            listContainer.add(addBtn);

            List<TaxonDto> all     = getTaxonCatalogService().listAllByType(getTaxonType(), Locale.ENGLISH, true);
            Map<Long, Long> counts = all.isEmpty() ? Map.of() : getTaxonCatalogService().getUsageCounts(getTaxonType());

            List<TaxonDto> active  = all.stream().filter(t -> !t.isDeleted()).toList();
            List<TaxonDto> deleted = all.stream().filter(TaxonDto::isDeleted).toList();

            if (active.isEmpty() && deleted.isEmpty()) {
                Span empty = new Span(getSupport().getI18n().get(getLabels().empty()));
                empty.addClassName("taxon-empty-state");
                listContainer.add(empty);
                return;
            }

            active.forEach(t  -> listContainer.add(buildRow(t, counts.getOrDefault(t.getId(), 0L), false)));
            deleted.forEach(t -> listContainer.add(buildRow(t, counts.getOrDefault(t.getId(), 0L), true)));
        } catch (Exception ex) {
            log.error("Failed to refresh {} list", getTaxonType(), ex);
            getSupport().getNotification().error(getLabels().notificationDeleteError(), ex.getMessage());
            listContainer.removeAll();
        }
    }

    private Div buildRow(TaxonDto entity, long usageCount, boolean isDeleted) {
        Span nameSpan = new Span(entity.getName());
        nameSpan.addClassName("taxon-row-name");
        if (isDeleted) nameSpan.addClassName("taxon-row-deleted");

        Span deletedBadge = new Span(getSupport().getI18n().get(getLabels().deletedLabel()));
        deletedBadge.addClassName("taxon-deleted-badge");
        deletedBadge.setVisible(isDeleted);

        Span countSpan = new Span(getSupport().getI18n().get(REFERENCE_DATA_USAGE_COUNT, usageCount));
        countSpan.addClassName("taxon-row-count");

        Div actions = buildRowActions(entity, isDeleted);

        HorizontalLayout row = new HorizontalLayout(nameSpan, deletedBadge, countSpan, actions);
        row.addClassName("taxon-row");
        row.setAlignItems(HorizontalLayout.Alignment.CENTER);

        if (!isDeleted) {
            nameSpan.addClickListener(_ -> getOverlay().openForView(entity, this::updateRowInPlace));
        }

        Div wrapper = new Div(row);
        wrapper.addClassName("taxon-row-wrapper");
        wrapper.getElement().setAttribute("data-taxon-id", String.valueOf(entity.getId()));

        return wrapper;
    }

    private void updateRowInPlace(TaxonDto fresh) {
        String id = String.valueOf(fresh.getId());
        listContainer.getChildren()
                .filter(c -> id.equals(c.getElement().getAttribute("data-taxon-id")))
                .findFirst()
                .ifPresent(old -> {
                    int index = listContainer.indexOf(old);
                    long usageCount = getTaxonCatalogService().getUsageCounts(getTaxonType()).getOrDefault(fresh.getId(), 0L);
                    Div updated = buildRow(fresh, usageCount, fresh.isDeleted());
                    listContainer.remove(old);
                    listContainer.addComponentAtIndex(index, updated);
                });
    }

    private Div buildRowActions(TaxonDto entity, boolean isDeleted) {
        Div actions = new Div();
        actions.addClassName("taxon-row-actions");

        if (isDeleted) {
            UiIconButton restoreBtn = new UiIconButton(getSupport().getI18n().get(getLabels().tooltipRestore()), VaadinIcon.ARROW_BACKWARD.create());
            restoreBtn.addClickListener(e -> doRestore(entity));
            actions.add(restoreBtn);
        } else {
            UiIconButton editBtn = new UiIconButton(getSupport().getI18n().get(getLabels().tooltipEdit()), VaadinIcon.PENCIL.create());
            editBtn.addClickListener(e -> getOverlay().openForEdit(entity, this::updateRowInPlace));

            UiIconButton deleteBtn = new UiIconButton(getSupport().getI18n().get(getLabels().tooltipDelete()), VaadinIcon.TRASH.create());
            deleteBtn.addClickListener(e -> confirmAndDelete(entity));

            actions.add(editBtn, deleteBtn);
        }
        return actions;
    }

    private void confirmAndDelete(TaxonDto entity) {
        new ConfirmActionDialog(
                getSupport().getI18n().get(getLabels().confirmDeleteTitle()),
                getSupport().getI18n().get(getLabels().confirmDeleteText(), entity.getName()),
                getSupport().getI18n().get(getLabels().confirmDeleteButton()),
                getSupport().getI18n().get(getLabels().confirmCancelButton()),
                () -> {
                    try {
                        getTaxonCatalogService().softDelete(entity.getId(), getAccess().getCurrentUserId(), entity.getVersion());
                        getSupport().getNotification().success(getLabels().notificationDeleted());
                        refresh();
                    } catch (AccessDeniedException e) {
                        log.warn("Access denied deleting {} id={}: {}", getTaxonType(), entity.getId(), e.getMessage());
                        getSupport().getNotification().accessDenied();
                    } catch (Exception e) {
                        log.error("Error deleting {} id={}", getTaxonType(), entity.getId(), e);
                        getSupport().getNotification().error(getLabels().notificationDeleteError(), e.getMessage());
                    }
                }
        ).open();
    }

    private void doRestore(TaxonDto entity) {
        try {
            getTaxonCatalogService().restore(entity.getId(), getAccess().getCurrentUserId());
            getSupport().getNotification().success(getLabels().notificationRestored());
            refresh();
        } catch (AccessDeniedException e) {
            log.warn("Access denied restoring {} id={}: {}", getTaxonType(), entity.getId(), e.getMessage());
            getSupport().getNotification().accessDenied();
        } catch (Exception e) {
            log.error("Error restoring {} id={}", getTaxonType(), entity.getId(), e);
            getSupport().getNotification().error(getLabels().notificationDeleteError(), e.getMessage());
        }
    }
}
