package org.ost.advertisement.ui.utils;

import static org.ost.advertisement.ui.utils.SupportUtil.hasChanged;

import com.vaadin.flow.component.AbstractField;
import java.util.Set;

public class FilterHighlighterUtil {

	private static final String BACKGROUND_COLOR = "background-color";

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

	public static <T> void highlight(AbstractField<?, ?> field, T newValue, T originalValue, T defaultValue) {
		highlight(field, newValue, originalValue, defaultValue, true);
	}

	public static <T> void highlight(AbstractField<?, ?> field, T newValue, T originalValue, T defaultValue,
									 boolean isValid) {
		if (hasChanged(newValue, originalValue)) {
			field.getStyle().set(BACKGROUND_COLOR,
				isValid ? HighlightStyle.VALID.background() : HighlightStyle.INVALID.background());
			return;
		}
		if (hasChanged(originalValue, defaultValue)) {
			field.getStyle().set(BACKGROUND_COLOR, HighlightStyle.CHANGED.background());
			return;
		}
		dehighlight(field);
	}

	public static void dehighlight(Set<AbstractField<?, ?>> fields) {
		for (AbstractField<?, ?> field : fields) {
			dehighlight(field);
		}
	}

	public static void dehighlight(AbstractField<?, ?> field) {
		field.getStyle().remove(BACKGROUND_COLOR);
	}
}
