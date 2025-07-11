package org.ost.advertisement.ui.utils;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import java.time.Instant;
import java.util.Objects;

public class FilterHighlighterUtil {

	private static final String HIGHLIGHT_COLOR = "#fff9c4"; // світло-жовтий

	public static void highlight(TextField field, String currentValue, String defaultValue) {
		boolean changed = !Objects.equals(currentValue, defaultValue);
		field.getStyle().set("background-color", changed ? HIGHLIGHT_COLOR : "");
	}

	public static void highlight(NumberField field, Long currentValue, Long defaultValue) {
		boolean changed = !Objects.equals(currentValue, defaultValue);
		field.getStyle().set("background-color", changed ? HIGHLIGHT_COLOR : "");
	}

	public static void highlight(DatePicker field, Instant current, Instant base) {
		boolean changed = !Objects.equals(current, base);
		field.getStyle().set("background-color", changed ? HIGHLIGHT_COLOR : "");
	}

	public static <T> void highlight(Select<T> field, T currentValue, T originalValue) {
		if (!Objects.equals(currentValue, originalValue)) {
			field.getStyle().set("background-color", "#ffeeba"); // Світло-жовтий
		} else {
			field.getStyle().remove("background-color");
		}
	}

	public static void clearHighlight(Component... components) {
		for (Component c : components) {
			c.getStyle().remove("background-color");
		}
	}
}
