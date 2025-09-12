package org.ost.advertisement.ui.views.components.filters;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import lombok.Getter;
import org.ost.advertisement.mappers.FilterMapper;
import org.ost.advertisement.services.ValidationService;

public abstract class AbstractFilterFields<F> {

	@Getter
	protected final FilterFieldsProcessor<F> filterFieldsProcessor;

	protected AbstractFilterFields(F defaultFilter, ValidationService<F> validation, FilterMapper<F> filterMapper) {
		this.filterFieldsProcessor = new FilterFieldsProcessor<>(filterMapper, validation, defaultFilter);
	}

	protected boolean isValidProperty(F filter, String property) {
		return filterFieldsProcessor.getValidation().isValidProperty(filter, property);
	}

	public abstract void eventProcessor(Runnable onApply);

	protected NumberField createNumberField(String placeholder) {
		NumberField field = new NumberField();
		field.setWidth("100px");
		field.setClearButtonVisible(true);
		field.setPlaceholder(placeholder);
		field.setValueChangeMode(ValueChangeMode.EAGER);
		return field;
	}

	protected TextField createFullTextField(String placeholder) {
		TextField field = createTextField(placeholder);
		field.setWidthFull();
		return field;
	}

	protected TextField createTextField(String placeholder) {
		TextField field = new TextField();
		field.setPlaceholder(placeholder);
		field.setClearButtonVisible(true);
		field.setValueChangeMode(ValueChangeMode.EAGER);
		return field;
	}

	protected <T> ComboBox<T> createCombo(String placeholder, T[] items) {
		ComboBox<T> comboBox = new ComboBox<>();
		comboBox.setItems(items);
		comboBox.setClearButtonVisible(true);
		comboBox.setPlaceholder(placeholder);
		comboBox.setWidth("100%");
		return comboBox;
	}

	protected DatePicker createDatePicker(String placeholder) {
		DatePicker field = new DatePicker();
		field.setWidth("140px");
		field.setPlaceholder(placeholder);
		return field;
	}

	protected VerticalLayout createFilterBlock(Component... components) {
		VerticalLayout layout = new VerticalLayout(components);
		layout.setPadding(false);
		layout.setSpacing(false);
		layout.setMargin(false);
		layout.getStyle().set("gap", "4px");
		return layout;
	}
}

