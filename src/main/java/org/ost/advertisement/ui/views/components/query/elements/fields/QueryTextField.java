package org.ost.advertisement.ui.views.components.query.elements.fields;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.i18n.I18nPlaceholderParams;

import static org.ost.advertisement.ui.utils.HighlighterUtil.setDefaultBorder;

@RequiredArgsConstructor
@SuppressWarnings("java:S110")
public class QueryTextField extends TextField {

    @Value
    @Builder
    public static class Parameters implements I18nPlaceholderParams {
        @NonNull I18nService i18n;
        @NonNull I18nKey placeholderKey;
    }

    private final transient @NonNull Parameters parameters;

    @PostConstruct
    private void initLayout() {
        addClassName("query-text");
        setPlaceholder(parameters.placeholder());
        setClearButtonVisible(true);
        setValueChangeMode(ValueChangeMode.EAGER);
        setDefaultBorder(this);
    }
}

