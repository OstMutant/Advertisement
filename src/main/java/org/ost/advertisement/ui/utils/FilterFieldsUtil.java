package org.ost.advertisement.ui.utils;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Objects;

public class FilterFieldsUtil {

	public static NumberField createNumberField(String placeholder) {
		NumberField field = new NumberField();
		field.setWidth("100px");
		field.setClearButtonVisible(true);
		field.setPlaceholder(placeholder);
		field.setValueChangeMode(ValueChangeMode.EAGER);
		return field;
	}

	public static TextField createFullTextField(String placeholder) {
		TextField field = createTextField(placeholder);
		field.setWidthFull();
		return field;
	}

	public static TextField createShortTextField(String placeholder) {
		TextField field = createTextField(placeholder);
		field.setWidth("140px");;
		return field;
	}

	public static TextField createTextField(String placeholder) {
		TextField field = new TextField();
		field.setPlaceholder(placeholder);
		field.setClearButtonVisible(true);
		field.setValueChangeMode(ValueChangeMode.EAGER);
		return field;
	}

	public static DatePicker createDatePicker(String placeholder) {
		DatePicker field = new DatePicker();
		field.setWidth("140px");
		field.setPlaceholder(placeholder);
		return field;
	}

	public static Button createButton(VaadinIcon icon, String tooltip, ButtonVariant variant) {
		Button button = new Button(icon.create());
		button.setText("");
		button.addThemeVariants(variant, ButtonVariant.LUMO_ICON);
		button.getElement().setProperty("title", tooltip);
		return button;
	}

	public static Button createTextIconButton(String text, VaadinIcon icon, ButtonVariant... variants) {
		Button button = new Button(text, icon.create());
		button.addThemeVariants(variants);
		return button;
	}

	public static VerticalLayout createFilterBlock(Component... components) {
		VerticalLayout layout = new VerticalLayout(components);
		layout.setPadding(false);
		layout.setSpacing(false);
		layout.setMargin(false);
		layout.getStyle().set("gap", "4px");
		return layout;
	}

	public static <T> Select<T> createSelect(String placeholder, Collection<T> items) {
		Select<T> select = new Select<>();
		select.setItems(items);
		select.setPlaceholder(placeholder);
		select.setWidth("140px");
		select.setEmptySelectionAllowed(true);
		select.setEmptySelectionCaption("Any");
		return select;
	}

	public static void clearAll(HasValue<?, ?>... fields) {
		for (HasValue<?, ?> field : fields) {
			field.clear();
		}
	}

	public static Instant toInstant(LocalDate date) {
		return date != null ? date.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
	}

	public static Long toLong(Double value) {
		return value != null ? value.longValue() : null;
	}

	public static boolean isValidNumberRange(Long min, Long max) {
		return min == null || max == null || min <= max;
	}

	public static boolean isValidDateRange(Instant start, Instant end) {
		return start == null || end == null || !start.isAfter(end);
	}

	public static <T> boolean hasChanged(T current, T original) {
		return !Objects.equals(current, original);
	}
}
