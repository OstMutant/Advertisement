package org.ost.marketplace.ui.query.elements.fields;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.marketplace.i18n.TranslationKey;
import org.ost.marketplace.i18n.I18nService;
import org.ost.platform.ui.Configurable;
import org.ost.marketplace.i18n.Translatable;
import org.ost.platform.ui.Initialization;
import org.ost.marketplace.ui.query.utils.TimeZoneUtil;
import org.springframework.context.annotation.Scope;

import java.time.*;

import static org.ost.marketplace.ui.query.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class QueryDateTimeField extends CustomField<LocalDateTime>
        implements Configurable<QueryDateTimeField, QueryDateTimeField.Parameters>, Translatable, Initialization<QueryDateTimeField> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull TranslationKey datePlaceholderKey;
        @NonNull TranslationKey timePlaceholderKey;
        boolean          isEnd;
    }

    @Getter
    private final transient I18nService i18nService;

    private boolean isEnd;

    private final DatePicker datePicker = new DatePicker();
    private final TimePicker timePicker = new TimePicker();

    public QueryDateTimeField(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @Override
    @PostConstruct
    public QueryDateTimeField init() {
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
    public QueryDateTimeField configure(Parameters p) {
        this.isEnd = p.isEnd();
        datePicker.setPlaceholder(getValue(p.getDatePlaceholderKey()));
        timePicker.setPlaceholder(getValue(p.getTimePlaceholderKey()));
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
}
