package org.ost.ui.query;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import lombok.Getter;
import org.ost.marketplace.i18n.I18nService;
import org.ost.marketplace.i18n.TranslationKey;
import org.springframework.data.domain.Sort.Direction;

import java.util.List;
import java.util.function.BiFunction;

public class QueryStatusBar<T> extends VerticalLayout {

    public record Labels(
            TranslationKey filtersNone,
            TranslationKey filtersPrefix,
            TranslationKey sortNone,
            TranslationKey sortPrefix,
            TranslationKey sortAsc,
            TranslationKey sortDesc) {
    }

    private final Span filterInfo = span("query-status-bar-filter-info");
    private final Span sortInfo   = span("query-status-bar-sort-info");
    private final Span separator  = spanWithText("|", "query-status-bar-separator");
    private final Span toggleIcon = spanWithText("▸", "query-status-bar-toggle-icon");

    private final transient I18nService   i18n;
    private final transient Labels        labels;
    @Getter
    private final transient QueryBlock<T> queryBlock;

    public QueryStatusBar(I18nService i18n, QueryBlock<T> queryBlock, Labels labels) {
        this.i18n       = i18n;
        this.labels     = labels;
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
        filterInfo.setText(buildStatusText(filters, labels.filtersNone(), labels.filtersPrefix()));

        List<String> sorts = queryBlock.getSortProcessor().loopSortDescriptions(getSortDescriptionFunction());
        sortInfo.setText(buildStatusText(sorts, labels.sortNone(), labels.sortPrefix()));

        separator.setVisible(!filters.isEmpty() || !sorts.isEmpty());
    }

    public void toggleVisibility() {
        boolean open = queryBlock.toggleVisibility();
        toggleIcon.setText(open ? "▾" : "▸");
    }

    private String buildStatusText(List<String> items, TranslationKey noneKey, TranslationKey prefixKey) {
        return items.isEmpty() ? i18n.get(noneKey) : i18n.get(prefixKey) + " " + String.join(", ", items);
    }

    private BiFunction<TranslationKey, Direction, String> getSortDescriptionFunction() {
        return (i18nKey, direction) -> {
            String label          = i18n.get(i18nKey);
            String directionLabel = switch (direction) {
                case Direction.ASC  -> i18n.get(labels.sortAsc());
                case Direction.DESC -> i18n.get(labels.sortDesc());
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
