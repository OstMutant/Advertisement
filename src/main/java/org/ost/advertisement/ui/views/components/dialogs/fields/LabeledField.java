package org.ost.advertisement.ui.views.components.dialogs.fields;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;

import java.util.List;

public class LabeledField extends HorizontalLayout {

    @Value
    @Builder
    public static class Parameters {
        @NonNull I18nService i18n;
        @NonNull I18nKey labelKey;
        @Singular List<String> cssClasses;
    }

    private final Span valueSpan = new Span();

    protected LabeledField(@NonNull Parameters p) {
        String[] css = p.getCssClasses().toArray(new String[0]);

        Span labelSpan = new Span(p.getI18n().get(p.getLabelKey()));
        labelSpan.addClassNames(css);
        valueSpan.addClassNames(css);

        add(labelSpan, valueSpan);
        setAlignItems(Alignment.BASELINE);
        addClassName("labeled-field");
    }

    public void update(String value) {
        valueSpan.setText(value != null ? value : "");
    }
}