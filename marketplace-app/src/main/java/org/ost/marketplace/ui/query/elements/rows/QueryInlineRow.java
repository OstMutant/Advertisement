package org.ost.marketplace.ui.query.elements.rows;
import org.ost.marketplace.services.i18n.I18nKey;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;

import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.Configurable;

import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.query.elements.SortIcon;
import org.springframework.context.annotation.Scope;

import java.util.List;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class QueryInlineRow extends HorizontalLayout
        implements Configurable<QueryInlineRow, QueryInlineRow.Parameters>, Initialization<QueryInlineRow> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull  I18nKey         labelKey;
        @NonNull  SortIcon        sortIcon;
        @Singular List<Component> filterFields;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    @PostConstruct
    public QueryInlineRow init() {
        addClassName("query-inline-row");
        return this;
    }

    @Override
    public QueryInlineRow configure(Parameters p) {
        HorizontalLayout labelAndSort = new HorizontalLayout(new Span(i18nService.get(p.getLabelKey())), p.getSortIcon());
        labelAndSort.addClassName("query-inline-label-sort");
        HorizontalLayout filters = new HorizontalLayout(p.getFilterFields().toArray(new Component[0]));
        filters.addClassName("query-inline-filters");
        add(labelAndSort, filters);
        return this;
    }
}
