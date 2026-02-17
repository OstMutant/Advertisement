package org.ost.advertisement.ui.views.components.query.elements.rows;

import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;
import org.ost.advertisement.ui.views.components.query.elements.fields.QueryDateTimeField;

@Getter
public class QueryDateInlineRow extends QueryInlineRow {

    @Value
    @Builder
    public static class Parameters {
        @NonNull I18nService i18n;
        @NonNull I18nKey labelI18nKey;
        @NonNull SortIcon sortIcon;
        @NonNull QueryDateTimeField startDate;
        @NonNull QueryDateTimeField endDate;
    }

    private final SortIcon sortIcon;
    private final QueryDateTimeField startDate;
    private final QueryDateTimeField endDate;

    public QueryDateInlineRow(@NonNull Parameters parameters) {
        super(parameters.getI18n(), parameters.getLabelI18nKey());
        sortIcon = parameters.getSortIcon();
        startDate = parameters.getStartDate();
        endDate = parameters.getEndDate();
    }

    @PostConstruct
    private void initLayout() {
        initLayout(sortIcon, startDate, endDate);
    }
}
