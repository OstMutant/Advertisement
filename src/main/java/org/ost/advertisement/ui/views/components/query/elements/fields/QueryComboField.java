package org.ost.advertisement.ui.views.components.query.elements.fields;

import com.vaadin.flow.component.combobox.ComboBox;
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
public class QueryComboField<T> extends ComboBox<T> {

    @Value
    @Builder
    public static class Parameters<T> implements I18nPlaceholderParams {
        @NonNull I18nService i18n;
        @NonNull I18nKey placeholderKey;
        @NonNull T[] items;
    }

    private final transient @NonNull Parameters<T> parameters;

    @PostConstruct
    private void initLayout() {
        addClassName("query-combo");
        setPlaceholder(parameters.placeholder());
        setClearButtonVisible(true);
        setItems(parameters.items);
        setDefaultBorder(this);
    }
}
