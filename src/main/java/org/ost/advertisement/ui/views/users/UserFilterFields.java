package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.toInstant;
import static org.ost.advertisement.utils.FilterUtil.isValidDateRange;
import static org.ost.advertisement.utils.FilterUtil.isValidNumberRange;
import static org.ost.advertisement.utils.FilterUtil.toLong;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import java.util.function.Predicate;
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

	@Override
	public void configure(Runnable onApply) {
		super.configure(onApply);
		Predicate<UserFilter> validationId = f -> isValidNumberRange(f.getStartId(), f.getEndId());
		register(idMin, (f, v) -> f.setStartId(toLong(v)), UserFilter::getStartId, validationId);
		register(idMax, (f, v) -> f.setEndId(toLong(v)), UserFilter::getEndId, validationId);

		register(nameField, (f, v) -> f.setNameFilter(v == null ? null : v.isBlank() ? null : v),
			UserFilter::getNameFilter, f -> true);
		register(roleCombo, UserFilter::setRole, UserFilter::getRole, f -> true);

		Predicate<UserFilter> validationCreatedAt = f -> isValidDateRange(f.getCreatedAtStart(), f.getCreatedAtEnd());
		register(createdStart, (f, v) -> f.setCreatedAtStart(toInstant(v)),
			UserFilter::getCreatedAtStart, validationCreatedAt);
		register(createdEnd, (f, v) -> f.setCreatedAtEnd(toInstant(v)),
			UserFilter::getCreatedAtEnd, validationCreatedAt);

		Predicate<UserFilter> validationUpdatedAt = f -> isValidDateRange(f.getCreatedAtStart(), f.getCreatedAtEnd());
		register(updatedStart, (f, v) -> f.setUpdatedAtStart(toInstant(v)),
			UserFilter::getUpdatedAtStart, validationUpdatedAt);
		register(updatedEnd, (f, v) -> f.setUpdatedAtEnd(toInstant(v)),
			UserFilter::getUpdatedAtEnd, validationUpdatedAt);
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
