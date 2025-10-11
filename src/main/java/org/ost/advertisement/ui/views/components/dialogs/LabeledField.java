package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.ost.advertisement.constans.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.TailwindStyle;

public class LabeledField extends HorizontalLayout {

	private final I18nService i18n;
	private final Span label = new Span();
	private final Span value = new Span();

	public LabeledField(I18nService i18n, TailwindStyle... styles) {
		this.i18n = i18n;
		add(label, value);
		setAlignItems(Alignment.BASELINE);
		setSpacing(true);
		TailwindStyle.applyAll(label, styles);
		TailwindStyle.applyAll(value, styles);
	}

	public void set(I18nKey labelKey, String text) {
		label.setText(i18n.get(labelKey));
		value.setText(text);
	}
}
