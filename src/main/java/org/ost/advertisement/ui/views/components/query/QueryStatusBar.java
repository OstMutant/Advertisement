package org.ost.advertisement.ui.views.components.query;


import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.filter.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.sort.SortProcessor;
import org.springframework.data.domain.Sort.Order;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class QueryStatusBar<T> extends HorizontalLayout {

    private final Span filterInfo = new Span();
    private final Span sortInfo = new Span();
    private final Span separator = new Span("|");
    private final Span toggleIcon = new Span();

    private final transient I18nService i18n;
    private final transient QueryBlockLayout queryBlockLayout;
    private final transient UnaryOperator<String> sortLabelProvider;

    public QueryStatusBar(I18nService i18n,
                          QueryBlock<T> queryBlock,
                          QueryBlockLayout queryBlockLayout,
                          UnaryOperator<String> sortLabelProvider) {
        this.i18n = i18n;
        this.queryBlockLayout = queryBlockLayout;
        this.sortLabelProvider = sortLabelProvider;

        applyStyles();
        initToggleIcon();

        add(toggleIcon, filterInfo, separator, sortInfo);
        update(queryBlock.getFilterProcessor(), queryBlock.getSortProcessor());

        getElement().addEventListener("click", e -> toggleVisibility());
    }

    public void update(FilterProcessor<?> filterProcessor, SortProcessor sortProcessor) {
        List<String> filters = filterProcessor.getActiveFilterDescriptions();

        filterInfo.setText(filters.isEmpty()
                ? i18n.get(I18nKey.QUERY_STATUS_FILTERS_NONE)
                : i18n.get(I18nKey.QUERY_STATUS_FILTERS_PREFIX) + " " + String.join(", ", filters));

        List<String> sorts = sortProcessor.getSortDescriptions(getSortDescriptionFunction(i18n, sortLabelProvider));
        sortInfo.setText(sorts.isEmpty()
                ? i18n.get(I18nKey.QUERY_STATUS_SORT_NONE)
                : i18n.get(I18nKey.QUERY_STATUS_SORT_PREFIX) + " " + String.join(", ", sorts));

        separator.setVisible(!filters.isEmpty() || !sorts.isEmpty());
    }

    private Function<Order, String> getSortDescriptionFunction(I18nService i18n, UnaryOperator<String> labelProvider) {
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
        setToggleIconState(queryBlockLayout.toggleVisibility());
    }

    private void setToggleIconState(boolean isOpen) {
        toggleIcon.setText(isOpen ? "▾" : "▸");
    }

    private void initToggleIcon() {
        setToggleIconState(false);
        toggleIcon.getStyle().set("margin-right", "8px").set("font-weight", "bold");
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

        filterInfo.getStyle().set("font-weight", "500");
        sortInfo.getStyle().set("font-weight", "500");
        separator.getStyle().set("margin", "0 8px").set("color", "#999");
    }
}

