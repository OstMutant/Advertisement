package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
public class LabeledField extends HorizontalLayout {

    private final Span label = new Span();
    private final Span value = new Span();

    private LabeledField() {
        add(label, value);
        setAlignItems(Alignment.BASELINE);
        setSpacing(true);
    }

    private void setLabel(String text) {
        label.setText(text);
    }

    private void setValue(String text) {
        value.setText(text);
    }

    @SpringComponent
    public static class Builder {

        private final ObjectProvider<LabeledField> provider;
        private final I18nService i18n;

        public Builder(I18nService i18n, ObjectProvider<LabeledField> provider) {
            this.i18n = i18n;
            this.provider = provider;
        }

        private I18nKey labelKey;
        private String valueText;
        private String[] cssClasses;

        public Builder withLabel(I18nKey key) {
            this.labelKey = key;
            return this;
        }

        public Builder withValue(String text) {
            this.valueText = text;
            return this;
        }

        public Builder withCssClasses(String... cssClasses) {
            this.cssClasses = cssClasses;
            return this;
        }

        public LabeledField build() {
            LabeledField field = provider.getObject();
            if (cssClasses != null) {
                field.label.addClassNames(cssClasses);
                field.value.addClassNames(cssClasses);
            }
            if (i18n != null) {
                field.setLabel(i18n.get(labelKey));
            }
            field.setValue(valueText);
            return field;
        }
    }
}
