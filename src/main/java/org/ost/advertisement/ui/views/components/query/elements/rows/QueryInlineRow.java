package org.ost.advertisement.ui.views.components.query.elements.rows;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;

@RequiredArgsConstructor
public class QueryInlineRow extends HorizontalLayout {
    private final transient I18nService i18n;
    private final I18nKey labelI18nKey;

    protected void initLayout(SortIcon sortIcon, Component... filterFields) {
        HorizontalLayout labelAndSort = new HorizontalLayout(new Span(i18n.get(labelI18nKey)), sortIcon);
        labelAndSort.addClassName("query-inline-label-sort");
        HorizontalLayout filters = new HorizontalLayout(filterFields);
        filters.addClassName("query-inline-filters");
        addClassName("query-inline-row");
        add(labelAndSort, filters);
    }
}
