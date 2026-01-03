package org.ost.advertisement.ui.views.components.query;


import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.bar.FilterInfoSpan;
import org.ost.advertisement.ui.views.components.query.elements.bar.SeparatorSpan;
import org.ost.advertisement.ui.views.components.query.elements.bar.SortInfoSpan;
import org.ost.advertisement.ui.views.components.query.elements.bar.ToggleIconSpan;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class QueryStatusBar<T> extends HorizontalLayout {

    private final FilterInfoSpan filterInfo = new FilterInfoSpan();
    private final SortInfoSpan sortInfo = new SortInfoSpan();
    private final SeparatorSpan separator = new SeparatorSpan();
    private final ToggleIconSpan toggleIcon = new ToggleIconSpan();

    private final transient I18nService i18n;
    private final transient QueryBlockLayout queryBlockLayout;
    private final transient UnaryOperator<String> sortLabelProvider;
    private final transient QueryBlock<T> queryBlock;

    public QueryStatusBar(I18nService i18n,
                          QueryBlock<T> queryBlock,
                          QueryBlockLayout queryBlockLayout,
                          UnaryOperator<String> sortLabelProvider) {
        this.i18n = i18n;
        this.queryBlock = queryBlock;
        this.queryBlockLayout = queryBlockLayout;
        this.sortLabelProvider = sortLabelProvider;

        applyStyles();
        add(toggleIcon, filterInfo, separator, sortInfo);
        update();

        getElement().addEventListener("click", e -> toggleVisibility());
    }

    public void update() {
        List<String> filters = queryBlock.getFilterProcessor().getActiveFilterDescriptions();
        filterInfo.setText(buildStatusText(filters, I18nKey.QUERY_STATUS_FILTERS_NONE, I18nKey.QUERY_STATUS_FILTERS_PREFIX));

        List<String> sorts = queryBlock.getSortProcessor().getSortDescriptions(getSortDescriptionFunction(sortLabelProvider));
        sortInfo.setText(buildStatusText(sorts, I18nKey.QUERY_STATUS_SORT_NONE, I18nKey.QUERY_STATUS_SORT_PREFIX));

        separator.setVisible(!filters.isEmpty() || !sorts.isEmpty());
    }

    private String buildStatusText(List<String> items, I18nKey noneKey, I18nKey prefixKey) {
        return items.isEmpty() ? i18n.get(noneKey) : i18n.get(prefixKey) + " " + String.join(", ", items);
    }

    private Function<Sort.Order, String> getSortDescriptionFunction(UnaryOperator<String> labelProvider) {
        return order -> {
            String label = labelProvider.apply(order.getProperty());
            String direction = switch (order.getDirection()) {
                case ASC -> i18n.get(I18nKey.SORT_DIRECTION_ASC);
                case DESC -> i18n.get(I18nKey.SORT_DIRECTION_DESC);
            };
            return label + " (" + direction + ")";
        };
    }

    public void toggleVisibility() {
        toggleIcon.setOpen(queryBlockLayout.toggleVisibility());
    }

    private void applyStyles() {
        setWidthFull();
        setSpacing(true);
        setPadding(false);
        setAlignItems(Alignment.BASELINE);

        getStyle()
                .set("font-size", "0.85rem")
                .set("color", "#444")
                .set("background-color", "#f9f9f9")
                .set("border-radius", "6px")
                .set("padding", "8px 12px")
                .set("margin-bottom", "12px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.05)")
                .set("flex-wrap", "wrap")
                .set("cursor", "pointer");
    }
}
