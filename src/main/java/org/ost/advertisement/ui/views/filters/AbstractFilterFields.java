package org.ost.advertisement.ui.views.filters;

import static org.ost.advertisement.utils.FilterUtil.hasChanged;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import org.ost.advertisement.dto.Filter;

public abstract class AbstractFilterFields<F extends Filter<F>> {

	protected final F defaultFilter;
	@Getter
	protected final F originalFilter;
	@Getter
	protected final F newFilter;
	private final Set<AbstractField<?, ?>> filterFields = new HashSet<>();
	
	protected Button applyButton = createButton(VaadinIcon.FILTER, "Apply filters", ButtonVariant.LUMO_PRIMARY);
	protected Button clearButton = createButton(VaadinIcon.ERASER, "Clear filters", ButtonVariant.LUMO_TERTIARY);

	protected AbstractFilterFields(F defaultFilter) {
		this.defaultFilter = defaultFilter;
		this.originalFilter = defaultFilter.copy();
		this.newFilter = defaultFilter.copy();
	}

	public void configure(Runnable onApply) {
		applyButton.addClickListener(e -> {
			if (!validate()) {
				return;
			}
			originalFilter.copyFrom(newFilter);
			onApply.run();
			updateState();
		});
		clearButton.addClickListener(e -> {
			clearAllFields();
			newFilter.clear();
			originalFilter.clear();
			onApply.run();
			updateState();
		});
	}

	protected abstract void highlightChangedFields();

	protected <T, C extends AbstractField<?, T>> void register(C field, Consumer<T> setter) {
		filterFields.add(field);
		field.addValueChangeListener(e -> {
			setter.accept(e.getValue());
			updateState();
		});
	}

	protected void clearAllFields() {
		for (AbstractField<?, ?> field : filterFields) {
			field.clear();
		}
	}

	protected void updateState() {
		updateButtonState();
		highlightChangedFields();
	}

	protected void updateButtonState() {
		applyButton.getStyle().remove("border");
		applyButton.getStyle().remove("border-radius");
		if (isFilterActive()) {
			applyButton.getStyle().set("border", "3px solid orange");
			applyButton.getStyle().set("border-radius", "4px");
		}
	}

	protected boolean isFilterActive() {
		return validate() && hasChanged(newFilter, originalFilter);
	}

	protected boolean validate() {
		return newFilter.isValid();
	}

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

	protected TextField createShortTextField(String placeholder) {
		TextField field = createTextField(placeholder);
		field.setWidth("140px");
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

	protected Button createButton(VaadinIcon icon, String tooltip, ButtonVariant variant) {
		Button button = new Button(icon.create());
		button.setText("");
		button.addThemeVariants(variant, ButtonVariant.LUMO_ICON);
		button.getElement().setProperty("title", tooltip);
		return button;
	}

	protected VerticalLayout createFilterBlock(Component... components) {
		VerticalLayout layout = new VerticalLayout(components);
		layout.setPadding(false);
		layout.setSpacing(false);
		layout.setMargin(false);
		layout.getStyle().set("gap", "4px");
		return layout;
	}

	protected <T> Select<T> createSelect(String placeholder, Collection<T> items) {
		Select<T> select = new Select<>();
		select.setItems(items);
		select.setPlaceholder(placeholder);
		select.setWidth("140px");
		select.setEmptySelectionAllowed(true);
		select.setEmptySelectionCaption("Any");
		return select;
	}
}

