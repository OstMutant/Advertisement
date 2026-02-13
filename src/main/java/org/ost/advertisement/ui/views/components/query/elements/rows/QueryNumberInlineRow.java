package org.ost.advertisement.ui.views.components.query.elements.rows;

import com.vaadin.flow.component.textfield.NumberField;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;

@Getter
public class QueryNumberInlineRow extends QueryInlineRow {

    @Value
    @Builder
    public static class Parameters {
        @NonNull
        I18nService i18n;
        @NonNull
        I18nKey labelI18nKey;
        @NonNull
        SortIcon sortIcon;
        @NonNull
        NumberField minField;
        @NonNull
        NumberField maxField;
    }

    private final SortIcon sortIcon;
    private final NumberField minField;
    private final NumberField maxField;

    public QueryNumberInlineRow(@NonNull Parameters parameters) {
        super(parameters.getI18n(), parameters.getLabelI18nKey());
        this.sortIcon = parameters.getSortIcon();
        this.minField = parameters.getMinField();
        this.maxField = parameters.getMaxField();
    }

    @PostConstruct
    private void initLayout() {
        initLayout(sortIcon, minField, maxField);
    }
}
