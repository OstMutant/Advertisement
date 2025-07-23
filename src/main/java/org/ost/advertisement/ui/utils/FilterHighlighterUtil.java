package org.ost.advertisement.ui.utils;

import static org.ost.advertisement.ui.utils.FilterFieldsUtil.hasChanged;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import java.util.List;

public class FilterHighlighterUtil {

	public enum HighlightStyle {
		CHANGED("rgba(200,230,201,0.5)"),
		INVALID("rgba(255,0,0,0.08)"),
		VALID("rgba(255,255,0,0.1)");

		private final String bg;

		HighlightStyle(String bg) {
			this.bg = bg;
		}

		public String background() {
			return bg;
		}
	}

	public static <T> void highlight(Component field, T newValue, T originalValue) {
		highlight(field, newValue, originalValue, true);
	}

	public static <T> void highlight(Component field, T newValue, T originalValue, boolean isValid) {
		if (hasChanged(newValue, originalValue)) {
			field.getStyle().set("background-color",
				isValid ? HighlightStyle.VALID.background() : HighlightStyle.INVALID.background());
			return;
		}
		field.getElement().getStyle().remove("background-color");
	}

	public static void dehighlight(List<AbstractField<?, ?>> fields) {
		for (Component c : fields) {
			c.getStyle().remove("background-color");
		}
	}
}
