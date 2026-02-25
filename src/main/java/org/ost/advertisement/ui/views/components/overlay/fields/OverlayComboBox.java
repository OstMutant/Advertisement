package org.ost.advertisement.ui.views.components.overlay.fields;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.builder.Configurable;
import org.ost.advertisement.ui.utils.i18n.I18nParams;
import org.springframework.context.annotation.Scope;

import java.util.List;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class OverlayComboBox<T> extends ComboBox<T> implements Configurable<OverlayComboBox<T>, OverlayComboBox.Parameters<T>>, I18nParams {

    @Getter
    private final transient I18nService i18nService;

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull I18nKey  labelKey;
        @NonNull List<T>  items;
        boolean           required;
    }

    @Override
    public OverlayComboBox<T> configure(Parameters<T> p) {
        setLabel(getValue(p.getLabelKey()));
        setItems(p.getItems());
        setRequired(p.isRequired());
        setAllowCustomValue(false);
        setWidthFull();
        addClassName("overlay-combo-box");
        return this;
    }
}
