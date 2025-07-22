package org.ost.advertisement.ui.views.filters;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
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
import lombok.Getter;

public abstract class AbstractFilterFields<TEntity, TFilter> {

	protected final TFilter defaultFilter;
	@Getter
	protected final TFilter filter;
	protected Button applyButton;
	protected Button clearButton;

	public AbstractFilterFields(TFilter defaultFilter) {
		this.defaultFilter = defaultFilter;
		this.filter = cloneFilter(defaultFilter);
	}

	protected abstract TFilter cloneFilter(TFilter original);

	protected abstract void clearAllFields();

	protected abstract void highlightChangedFields(boolean enable);

	protected abstract boolean isFilterActive();

	protected abstract void copyFilter(TFilter source, TFilter target);

	protected abstract void clearFilter(TFilter target);

	protected void updateState() {
		updateButtonState();
		highlightChangedFields(true);
	}

	protected void updateButtonState() {
		applyButton.removeThemeVariants(ButtonVariant.LUMO_CONTRAST);
		if (isFilterActive()) {
			applyButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
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

	public void clearAll(HasValue<?, ?>... fields) {
		for (HasValue<?, ?> field : fields) {
			field.clear();
		}
	}
}

