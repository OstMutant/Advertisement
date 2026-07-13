package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.AbstractSinglePropertyField;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;

@Tag("quill-editor")
@NpmPackage(value = "quill", version = "2.0.3")
@JsModule("./quill-editor.js")
public class QuillEditor extends AbstractSinglePropertyField<QuillEditor, String>
        implements HasSize, HasLabel {

    public QuillEditor() {
        super("value", "", false);
    }

    @Override
    public void setLabel(String label) {
        getElement().setAttribute("label", label != null ? label : "");
    }

    @Override
    public String getLabel() {
        return getElement().getAttribute("label");
    }

    public void setMaxLength(int maxLength) {
        getElement().setAttribute("maxlength", String.valueOf(maxLength));
    }
}
