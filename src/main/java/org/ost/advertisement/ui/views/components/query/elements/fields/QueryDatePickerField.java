package org.ost.advertisement.ui.views.components.query.elements.fields;

import com.vaadin.flow.component.datepicker.DatePicker;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;

import static org.ost.advertisement.ui.utils.HighlighterUtil.setDefaultBorder;

@RequiredArgsConstructor
public class QueryDatePickerField extends DatePicker {

    @Value @Builder
    public static class Parameters {
        @NonNull I18nService i18n;
        @NonNull I18nKey placeholderKey;
    }

    private final transient @NonNull Parameters parameters;

    @PostConstruct
    private void initLayout() {
        addClassName("query-date");
        setPlaceholder(parameters.getI18n().get(parameters.getPlaceholderKey()));
        setClearButtonVisible(true);
        setDefaultBorder(this);
    }
}
