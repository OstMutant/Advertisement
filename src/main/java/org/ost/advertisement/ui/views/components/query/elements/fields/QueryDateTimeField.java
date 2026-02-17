package org.ost.advertisement.ui.views.components.query.elements.fields;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.timepicker.TimePicker;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.TimeZoneUtil;

import java.time.*;

import static org.ost.advertisement.ui.utils.HighlighterUtil.setDefaultBorder;

public class QueryDateTimeField extends CustomField<LocalDateTime> {

    @Value
    @Builder
    public static class Parameters {
        @NonNull I18nService i18n;
        @NonNull I18nKey datePlaceholderKey;
        @NonNull I18nKey timePlaceholderKey;
        boolean isEnd;
    }

    private final DatePicker datePicker = new DatePicker();
    private final TimePicker timePicker = new TimePicker();
    private final transient Parameters parameters;

    public QueryDateTimeField(@NonNull Parameters parameters) {
        this.parameters = parameters;

        datePicker.setPlaceholder(parameters.getI18n().get(parameters.getDatePlaceholderKey()));
        timePicker.setPlaceholder(parameters.getI18n().get(parameters.getTimePlaceholderKey()));

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
    }

    @Override
    protected LocalDateTime generateModelValue() {
        LocalDate date = datePicker.getValue();
        LocalTime time = timePicker.getValue();

        if (date == null && time != null) {
            date = LocalDate.now(ZoneId.of(TimeZoneUtil.getClientTimeZoneId()));
        }

        if (time == null && date != null) {
            time = parameters.isEnd ? LocalTime.MAX : LocalTime.MIN;
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
}