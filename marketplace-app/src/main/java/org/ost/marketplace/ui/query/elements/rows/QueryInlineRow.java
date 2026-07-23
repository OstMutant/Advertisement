package org.ost.marketplace.ui.query.elements.rows;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.ost.marketplace.ui.query.elements.SortIcon;

public class QueryInlineRow extends HorizontalLayout {

    public QueryInlineRow(String label, SortIcon sortIcon, Component... filterFields) {
        addClassName("query-inline-row");

        Span labelSpan = new Span(label);
        HorizontalLayout labelAndSort = sortIcon != null
                ? new HorizontalLayout(labelSpan, sortIcon)
                : new HorizontalLayout(labelSpan);
        labelAndSort.addClassName("query-inline-label-sort");

        HorizontalLayout filters = new HorizontalLayout(filterFields);
        filters.addClassName("query-inline-filters");

        add(labelAndSort, filters);
    }

    public QueryInlineRow(String label, Component... filterFields) {
        this(label, null, filterFields);
    }
}
