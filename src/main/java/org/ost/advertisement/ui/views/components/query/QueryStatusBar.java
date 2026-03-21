package org.ost.advertisement.ui.views.components.query;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import lombok.Getter;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.springframework.data.domain.Sort.Direction;

import java.util.List;
import java.util.function.BiFunction;

public class QueryStatusBar<T> extends VerticalLayout {

    private final Span filterInfo = span("query-status-bar-filter-info");
    private final Span sortInfo   = span("query-status-bar-sort-info");
    private final Span separator  = spanWithText("|", "query-status-bar-separator");
    private final Span toggleIcon = spanWithText("▸", "query-status-bar-toggle-icon");

    private final transient I18nService      i18n;
    @Getter
    private final transient QueryBlock<T> queryBlock;

    public QueryStatusBar(I18nService i18n, QueryBlock<T> queryBlock) {
        this.i18n             = i18n;
        this.queryBlock = queryBlock;

        addClassName("query-status-bar");
        setPadding(false);
        setSpacing(false);

        HorizontalLayout row = new HorizontalLayout(toggleIcon, filterInfo, separator, sortInfo);
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.addClassName("query-status-bar-row");
        add(row);

        update();

        getElement().addEventListener("click", _ -> toggleVisibility());
    }

    public void update() {
        List<String> filters = queryBlock.getFilterProcessor().getActiveFilterDescriptions();
        filterInfo.setText(buildStatusText(filters, I18nKey.QUERY_STATUS_FILTERS_NONE, I18nKey.QUERY_STATUS_FILTERS_PREFIX));

        List<String> sorts = queryBlock.getSortProcessor().loopSortDescriptions(getSortDescriptionFunction());
        sortInfo.setText(buildStatusText(sorts, I18nKey.QUERY_STATUS_SORT_NONE, I18nKey.QUERY_STATUS_SORT_PREFIX));

        separator.setVisible(!filters.isEmpty() || !sorts.isEmpty());
    }

    public void toggleVisibility() {
        boolean open = queryBlock.toggleVisibility();
        toggleIcon.setText(open ? "▾" : "▸");
    }

    private String buildStatusText(List<String> items, I18nKey noneKey, I18nKey prefixKey) {
        return items.isEmpty() ? i18n.get(noneKey) : i18n.get(prefixKey) + " " + String.join(", ", items);
    }

    private BiFunction<I18nKey, Direction, String> getSortDescriptionFunction() {
        return (i18nKey, direction) -> {
            String label          = i18n.get(i18nKey);
            String directionLabel = switch (direction) {
                case Direction.ASC  -> i18n.get(I18nKey.SORT_DIRECTION_ASC);
                case Direction.DESC -> i18n.get(I18nKey.SORT_DIRECTION_DESC);
            };
            return label + " (" + directionLabel + ")";
        };
    }

    private static Span span(String cssClass) {
        Span s = new Span();
        s.addClassName(cssClass);
        return s;
    }

    private static Span spanWithText(String text, String cssClass) {
        Span s = span(cssClass);
        s.setText(text);
        return s;
    }
}