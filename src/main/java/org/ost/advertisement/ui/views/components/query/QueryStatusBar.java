package org.ost.advertisement.ui.views.components.query;


import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.bar.FilterInfoSpan;
import org.ost.advertisement.ui.views.components.query.elements.bar.SeparatorSpan;
import org.ost.advertisement.ui.views.components.query.elements.bar.SortInfoSpan;
import org.ost.advertisement.ui.views.components.query.elements.bar.ToggleIconSpan;
import org.springframework.data.domain.Sort.Direction;

import java.util.List;
import java.util.function.BiFunction;

public class QueryStatusBar<T> extends HorizontalLayout {

    private final FilterInfoSpan filterInfo = new FilterInfoSpan();
    private final SortInfoSpan sortInfo = new SortInfoSpan();
    private final SeparatorSpan separator = new SeparatorSpan();
    private final ToggleIconSpan toggleIcon = new ToggleIconSpan();

    private final transient I18nService i18n;
    private final transient QueryBlockLayout queryBlockLayout;
    private final transient QueryBlock<T> queryBlock;

    public QueryStatusBar(I18nService i18n,
                          QueryBlock<T> queryBlock,
                          QueryBlockLayout queryBlockLayout) {
        this.i18n = i18n;
        this.queryBlock = queryBlock;
        this.queryBlockLayout = queryBlockLayout;

        addClassName("query-status-bar");

        add(toggleIcon, filterInfo, separator, sortInfo);
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

    private String buildStatusText(List<String> items, I18nKey noneKey, I18nKey prefixKey) {
        return items.isEmpty() ? i18n.get(noneKey) : i18n.get(prefixKey) + " " + String.join(", ", items);
    }

    private BiFunction<I18nKey, Direction, String> getSortDescriptionFunction() {
        return (i18nKey, direction) -> {
            String label = i18n.get(i18nKey);
            String directionLabel = switch (direction) {
                case Direction.ASC -> i18n.get(I18nKey.SORT_DIRECTION_ASC);
                case Direction.DESC -> i18n.get(I18nKey.SORT_DIRECTION_DESC);
            };
            return label + " (" + directionLabel + ")";
        };
    }

    public void toggleVisibility() {
        toggleIcon.setOpen(queryBlockLayout.toggleVisibility());
    }

}
