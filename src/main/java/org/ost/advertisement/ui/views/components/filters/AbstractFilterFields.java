package org.ost.advertisement.ui.views.components.filters;

import static org.ost.advertisement.ui.utils.FilterHighlighterUtil.highlight;
import static org.ost.advertisement.utils.FilterUtil.hasChanged;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.Getter;
import org.ost.advertisement.dto.filter.Filter;

public abstract class AbstractFilterFields<F extends Filter<F>> {

	protected final F defaultFilter;
	@Getter
	protected final F originalFilter;
	@Getter
	protected final F newFilter;

	public record FilterFieldsRelationship<F, T>(
		AbstractField<?, ?> field,
		Function<F, T> getter,
		Predicate<F> validation
	) {

	}

	protected final Set<FilterFieldsRelationship<F, ?>> fieldsRelationships = new HashSet<>();

	protected AbstractFilterFields(F defaultFilter) {
		this.defaultFilter = defaultFilter;
		this.originalFilter = defaultFilter.copy();
		this.newFilter = defaultFilter.copy();
	}

	public abstract void configure(Runnable onApply);

	protected void highlightChangedFields() {
		for (FilterFieldsRelationship<F, ?> fieldRelationship : fieldsRelationships) {
			highlight(fieldRelationship.field, fieldRelationship.getter.apply(newFilter),
				fieldRelationship.getter.apply(originalFilter), fieldRelationship.getter.apply(defaultFilter)
				, fieldRelationship.validation.test(newFilter));
		}
	}

	protected <T, C extends AbstractField<?, T>, R> void register(C field, BiConsumer<F, T> setter,
																  Function<F, R> getter, Predicate<F> validation) {
		fieldsRelationships.add(new FilterFieldsRelationship<>(field, getter, validation));
		field.addValueChangeListener(e -> {
			setter.accept(newFilter, e.getValue());
			updateState();
		});
	}

	protected void clearAllFields() {
		for (FilterFieldsRelationship<?, ?> fieldRelationship : fieldsRelationships) {
			fieldRelationship.field.clear();
		}
	}

	protected void updateState() {
		highlightChangedFields();
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

