package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.ui.utils.FilterFieldsUtil.hasChanged;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.isValidDateRange;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.isValidNumberRange;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.toLong;
import static org.ost.advertisement.ui.utils.FilterHighlighterUtil.highlight;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.toInstant;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.Role;
import org.ost.advertisement.ui.views.filters.AbstractFilterFields;

public class UserFilterFields extends AbstractFilterFields<UserFilter> {

	private final NumberField idMin = createNumberField("Min ID");
	private final NumberField idMax = createNumberField("Max ID");
	private final TextField nameField = createFullTextField("Name...");
	private final ComboBox<Role> roleCombo = createCombo("Any role", Role.values());
	private final DatePicker createdStart = createDatePicker("Created from");
	private final DatePicker createdEnd = createDatePicker("Created to");
	private final DatePicker updatedStart = createDatePicker("Updated from");
	private final DatePicker updatedEnd = createDatePicker("Updated to");

	public UserFilterFields() {
		super(new UserFilter());
	}

	public void configure(Runnable onApply) {
		register(idMin, v -> newFilter.setStartId(toLong(v)));
		register(idMax, v -> newFilter.setEndId(toLong(v)));
		register(nameField, v -> newFilter.setNameFilter(v == null ? null : v.isBlank() ? null : v));
		register(roleCombo, newFilter::setRole);
		register(createdStart, v -> newFilter.setCreatedAtStart(toInstant(v)));
		register(createdEnd, v -> newFilter.setCreatedAtEnd(toInstant(v)));
		register(updatedStart, v -> newFilter.setUpdatedAtStart(toInstant(v)));
		register(updatedEnd, v -> newFilter.setUpdatedAtEnd(toInstant(v)));

		applyButton.addClickListener(e -> {
			if (!validate()) {
				return;
			}
			originalFilter.copyFrom(newFilter);
			updateState();
			onApply.run();
		});
		clearButton.addClickListener(e -> {
			clearAllFields();
			newFilter.clear();
			originalFilter.clear();
			updateState();
			onApply.run();
		});
	}

	@Override
	protected void highlightChangedFields() {
		boolean isIdValid = isValidNumberRange(newFilter.getStartId(), newFilter.getEndId());
		highlight(idMin, newFilter.getStartId(), originalFilter.getStartId(), defaultFilter.getStartId(), isIdValid);
		highlight(idMax, newFilter.getEndId(), originalFilter.getEndId(), defaultFilter.getEndId(), isIdValid);

		highlight(nameField, newFilter.getNameFilter(), originalFilter.getNameFilter(), defaultFilter.getNameFilter());
		highlight(roleCombo, newFilter.getRole(), originalFilter.getRole(), defaultFilter.getRole());

		boolean isCreatedAtValid = isValidDateRange(newFilter.getCreatedAtStart(), newFilter.getCreatedAtEnd());
		highlight(createdStart, newFilter.getCreatedAtStart(), originalFilter.getCreatedAtStart(),
			defaultFilter.getCreatedAtStart(), isCreatedAtValid);
		highlight(createdEnd, newFilter.getCreatedAtEnd(), originalFilter.getCreatedAtEnd(),
			defaultFilter.getCreatedAtEnd(), isCreatedAtValid);

		boolean isUpdatedAtValid = isValidDateRange(newFilter.getUpdatedAtStart(), newFilter.getUpdatedAtEnd());
		highlight(updatedStart, newFilter.getUpdatedAtStart(), originalFilter.getUpdatedAtStart(),
			defaultFilter.getUpdatedAtStart(), isUpdatedAtValid);
		highlight(updatedEnd, newFilter.getUpdatedAtEnd(), originalFilter.getUpdatedAtEnd(),
			defaultFilter.getUpdatedAtEnd(), isUpdatedAtValid);
	}

	@Override
	protected boolean isFilterActive() {
		return validate() && hasChanged(newFilter, originalFilter);
	}

	@Override
	protected boolean validate() {
		return isValidNumberRange(newFilter.getStartId(), newFilter.getEndId())
			&& isValidDateRange(newFilter.getCreatedAtStart(), newFilter.getCreatedAtEnd())
			&& isValidDateRange(newFilter.getUpdatedAtStart(), newFilter.getUpdatedAtEnd());
	}

	public Component getIdBlock() {
		return createFilterBlock(idMin, idMax);
	}

	public Component getNameBlock() {
		return nameField;
	}

	public Component getRoleBlock() {
		return roleCombo;
	}

	public Component getCreatedAtBlock() {
		return createFilterBlock(createdStart, createdEnd);
	}

	public Component getUpdatedAtBlock() {
		return createFilterBlock(updatedStart, updatedEnd);
	}

	public Component getActionBlock() {
		HorizontalLayout actions = new HorizontalLayout(applyButton, clearButton);
		actions.setSpacing(false);
		actions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return actions;
	}
}
