package org.ost.advertisement.ui.views.components.query.elements.rows;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.views.rules.Initialization;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.List;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class QueryInlineRow extends HorizontalLayout
        implements Configurable<QueryInlineRow, QueryInlineRow.Parameters>, I18nParams, Initialization<QueryInlineRow> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull  I18nKey         labelI18nKey;
        @NonNull  SortIcon        sortIcon;
        @Singular List<Component> filterFields;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<QueryInlineRow, Parameters> {
        @Getter
        private final ObjectProvider<QueryInlineRow> provider;
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
        HorizontalLayout labelAndSort = new HorizontalLayout(new Span(getValue(p.getLabelI18nKey())), p.getSortIcon());
        labelAndSort.addClassName("query-inline-label-sort");
        HorizontalLayout filters = new HorizontalLayout(p.getFilterFields().toArray(new Component[0]));
        filters.addClassName("query-inline-filters");
        add(labelAndSort, filters);
        return this;
    }
}
