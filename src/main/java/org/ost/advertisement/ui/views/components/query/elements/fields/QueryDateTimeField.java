package org.ost.advertisement.ui.views.components.query.elements.fields;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.builder.Configurable;
import org.ost.advertisement.ui.utils.builder.ComponentBuilder;
import org.ost.advertisement.ui.utils.I18nParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.ost.advertisement.ui.utils.TimeZoneUtil;

import java.time.*;

import static org.ost.advertisement.ui.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class QueryDateTimeField extends CustomField<LocalDateTime> implements Configurable<QueryDateTimeField, QueryDateTimeField.Parameters>, I18nParams {

    @Getter
    private final transient I18nService i18nService;

    private boolean isEnd;

    private final DatePicker datePicker = new DatePicker();
    private final TimePicker timePicker = new TimePicker();

    public QueryDateTimeField(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey datePlaceholderKey;
        @NonNull I18nKey timePlaceholderKey;
        boolean          isEnd;
    }

    @Override
    public QueryDateTimeField configure(Parameters p) {
        this.isEnd = p.isEnd();

        datePicker.setPlaceholder(getValue(p.getDatePlaceholderKey()));
        timePicker.setPlaceholder(getValue(p.getTimePlaceholderKey()));

        datePicker.setClearButtonVisible(true);
        timePicker.setClearButtonVisible(true);

        datePicker.addClassName("query-datetime-date");
        timePicker.addClassName("query-datetime-time");

        setDefaultBorder(datePicker);
        setDefaultBorder(timePicker);

        HorizontalLayout layout = new HorizontalLayout(datePicker, timePicker);
        layout.setAlignItems(Alignment.BASELINE);
        layout.addClassName("query-datetime-layout");

        add(layout);
        return this;
    }

    @Override
    protected LocalDateTime generateModelValue() {
        LocalDate date = datePicker.getValue();
        LocalTime time = timePicker.getValue();

        if (date == null && time != null) {
            date = LocalDate.now(ZoneId.of(TimeZoneUtil.getClientTimeZoneId()));
        }

        if (time == null && date != null) {
            time = isEnd ? LocalTime.MAX : LocalTime.MIN;
        }

        if (date == null) {
            return null;
        }

        return LocalDateTime.of(date, time);
    }

    @Override
    protected void setPresentationValue(LocalDateTime ldt) {
        if (ldt == null) {
            datePicker.clear();
            timePicker.clear();
        } else {
            datePicker.setValue(ldt.toLocalDate());
            timePicker.setValue(ldt.toLocalTime());
        }
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<QueryDateTimeField, Parameters> {
        @Getter
        private final ObjectProvider<QueryDateTimeField> provider;
    }
}