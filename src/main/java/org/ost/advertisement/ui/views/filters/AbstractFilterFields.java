package org.ost.advertisement.ui.views.filters;

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
import java.util.List;
import lombok.Getter;
import org.ost.advertisement.dto.Filter;

public abstract class AbstractFilterFields<F extends Filter<F>> {

	protected final F defaultFilter;
	@Getter
	protected final F originalFilter;
	@Getter
	protected final F newFilter;
	protected Button applyButton = createButton(VaadinIcon.FILTER, "Apply filters", ButtonVariant.LUMO_PRIMARY);
	protected Button clearButton = createButton(VaadinIcon.ERASER, "Clear filters", ButtonVariant.LUMO_TERTIARY);

	public AbstractFilterFields(F defaultFilter) {
		this.defaultFilter = defaultFilter;
		this.originalFilter = defaultFilter.copy();
		this.newFilter = defaultFilter.copy();
	}

	protected abstract void clearAllFields();

	protected abstract void dehighlightFields();

	protected abstract void highlightChangedFields();

	protected abstract boolean isFilterActive();

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

	protected boolean validate() {
		return true;
	}

	public NumberField createNumberField(String placeholder) {
		NumberField field = new NumberField();
		field.setWidth("100px");
		field.setClearButtonVisible(true);
		field.setPlaceholder(placeholder);
		field.setValueChangeMode(ValueChangeMode.EAGER);
		return field;
	}

	public TextField createFullTextField(String placeholder) {
		TextField field = createTextField(placeholder);
		field.setWidthFull();
		return field;
	}

	public TextField createShortTextField(String placeholder) {
		TextField field = createTextField(placeholder);
		field.setWidth("140px");
		return field;
	}

	public TextField createTextField(String placeholder) {
		TextField field = new TextField();
		field.setPlaceholder(placeholder);
		field.setClearButtonVisible(true);
		field.setValueChangeMode(ValueChangeMode.EAGER);
		return field;
	}

	public <T> ComboBox<T> createCombo(String placeholder, T[] items) {
		ComboBox<T> comboBox = new ComboBox<>();
		comboBox.setItems(items);
		comboBox.setClearButtonVisible(true);
		comboBox.setPlaceholder(placeholder);
		comboBox.setWidth("100%");
		return comboBox;
	}

	public DatePicker createDatePicker(String placeholder) {
		DatePicker field = new DatePicker();
		field.setWidth("140px");
		field.setPlaceholder(placeholder);
		return field;
	}

	public Button createButton(VaadinIcon icon, String tooltip, ButtonVariant variant) {
		Button button = new Button(icon.create());
		button.setText("");
		button.addThemeVariants(variant, ButtonVariant.LUMO_ICON);
		button.getElement().setProperty("title", tooltip);
		return button;
	}

	public VerticalLayout createFilterBlock(Component... components) {
		VerticalLayout layout = new VerticalLayout(components);
		layout.setPadding(false);
		layout.setSpacing(false);
		layout.setMargin(false);
		layout.getStyle().set("gap", "4px");
		return layout;
	}

	public <T> Select<T> createSelect(String placeholder, Collection<T> items) {
		Select<T> select = new Select<>();
		select.setItems(items);
		select.setPlaceholder(placeholder);
		select.setWidth("140px");
		select.setEmptySelectionAllowed(true);
		select.setEmptySelectionCaption("Any");
		return select;
	}

	public void clearAll(List<AbstractField<?, ?>> fields) {
		for (AbstractField<?, ?> field : fields) {
			field.clear();
		}
	}
}

