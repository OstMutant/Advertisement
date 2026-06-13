package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.marketplace.common.I18nKey;
import org.ost.marketplace.i18n.I18nService;
import org.ost.platform.ui.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.platform.ui.Initialization;
import org.springframework.context.annotation.Scope;

import java.util.List;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class UiComboBox<T> extends ComboBox<T>
        implements Configurable<UiComboBox<T>, UiComboBox.Parameters<T>>, I18nParams, Initialization<UiComboBox<T>> {

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull I18nKey  labelKey;
        @NonNull List<T>  items;
        boolean           required;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    @PostConstruct
    public UiComboBox<T> init() {
        setAllowCustomValue(false);
        setWidthFull();
        addClassName("combo-box");
        return this;
    }

    @Override
    public UiComboBox<T> configure(Parameters<T> p) {
        setLabel(getValue(p.getLabelKey()));
        setItems(p.getItems());
        setRequired(p.isRequired());
        getElement().setAttribute("data-testid", p.getLabelKey().toTestId());
        return this;
    }
}
