package org.ost.ui.query.elements.rows;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.marketplace.i18n.TranslationKey;
import org.ost.marketplace.i18n.I18nService;
import org.ost.platform.ui.Configurable;
import org.ost.marketplace.i18n.Translatable;
import org.ost.platform.ui.Initialization;
import org.ost.ui.query.elements.SortIcon;
import org.springframework.context.annotation.Scope;

import java.util.List;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class QueryInlineRow extends HorizontalLayout
        implements Configurable<QueryInlineRow, QueryInlineRow.Parameters>, Translatable, Initialization<QueryInlineRow> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull  TranslationKey         labelTranslationKey;
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
        HorizontalLayout labelAndSort = new HorizontalLayout(new Span(getValue(p.getLabelTranslationKey())), p.getSortIcon());
        labelAndSort.addClassName("query-inline-label-sort");
        HorizontalLayout filters = new HorizontalLayout(p.getFilterFields().toArray(new Component[0]));
        filters.addClassName("query-inline-filters");
        add(labelAndSort, filters);
        return this;
    }
}
