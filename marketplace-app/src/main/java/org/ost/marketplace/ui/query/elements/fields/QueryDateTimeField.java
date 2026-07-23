package org.ost.marketplace.ui.query.elements.fields;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.timepicker.TimePicker;
import org.ost.marketplace.ui.query.utils.TimeZoneUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.ost.marketplace.ui.query.utils.HighlighterUtil.setDefaultBorder;

public class QueryDateTimeField extends CustomField<LocalDateTime> {

    private final boolean isEnd;

    private final DatePicker datePicker = new DatePicker();
    private final TimePicker timePicker = new TimePicker();

    public QueryDateTimeField(String datePlaceholder, String timePlaceholder, boolean isEnd) {
        this.isEnd = isEnd;

        datePicker.setClearButtonVisible(true);
        timePicker.setClearButtonVisible(true);
        datePicker.setPlaceholder(datePlaceholder);
        timePicker.setPlaceholder(timePlaceholder);

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
}
