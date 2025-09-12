package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.ui.utils.SupportUtil.toLong;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.toInstant;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import java.util.function.Predicate;
import org.ost.advertisement.dto.filter.UserFilter;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.mappers.UserFilterMapper;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.filters.AbstractFilterFields;
import org.ost.advertisement.ui.views.components.filters.FilterActionsBlock;

@SpringComponent
@UIScope
public class UserFilterFields extends AbstractFilterFields<UserFilter> {

	private final NumberField idMin = createNumberField("Min ID");
	private final NumberField idMax = createNumberField("Max ID");
	private final TextField nameField = createFullTextField("Name...");
	private final TextField emailField = createFullTextField("Email");
	private final ComboBox<Role> roleCombo = createCombo("Any role", Role.values());
	private final DatePicker createdStart = createDatePicker("Created from");
	private final DatePicker createdEnd = createDatePicker("Created to");
	private final DatePicker updatedStart = createDatePicker("Updated from");
	private final DatePicker updatedEnd = createDatePicker("Updated to");
	private final FilterActionsBlock actionsBlock = new FilterActionsBlock();

	public UserFilterFields(UserFilterMapper filterMapper, ValidationService<UserFilter> validation) {
		super(UserFilter.empty(), UserFilter.empty(), UserFilter.empty(), validation, filterMapper);
	}

	@PostConstruct
	private void init() {
		Predicate<UserFilter> validationId = f -> isValidProperty(f, "startId") && isValidProperty(f, "endId");
		filterFieldsProcessor.register(idMin, (f, v) -> f.setStartId(toLong(v)), UserFilter::getStartId, validationId,
			actionsBlock);
		filterFieldsProcessor.register(idMax, (f, v) -> f.setEndId(toLong(v)), UserFilter::getEndId, validationId,
			actionsBlock);

		filterFieldsProcessor.register(nameField, (f, v) -> f.setName(v == null || v.isBlank() ? null : v),
			UserFilter::getName, f -> isValidProperty(f, "name"), actionsBlock);
		filterFieldsProcessor.register(emailField, (f, v) -> f.setEmail(v == null || v.isBlank() ? null : v),
			UserFilter::getEmail, f -> isValidProperty(f, "email"), actionsBlock);
		filterFieldsProcessor.register(roleCombo, UserFilter::setRole, UserFilter::getRole,
			f -> isValidProperty(f, "role"), actionsBlock);

		Predicate<UserFilter> validationCreatedAt = f -> isValidProperty(f, "createdAtStart") && isValidProperty(f,
			"createdAtEnd");
		filterFieldsProcessor.register(createdStart, (f, v) -> f.setCreatedAtStart(toInstant(v)),
			UserFilter::getCreatedAtStart, validationCreatedAt, actionsBlock);
		filterFieldsProcessor.register(createdEnd, (f, v) -> f.setCreatedAtEnd(toInstant(v)),
			UserFilter::getCreatedAtEnd, validationCreatedAt, actionsBlock);

		Predicate<UserFilter> validationUpdatedAt = f -> !isValidProperty(f, "updatedAtStart") && isValidProperty(f,
			"updatedAtEnd");
		filterFieldsProcessor.register(updatedStart, (f, v) -> f.setUpdatedAtStart(toInstant(v)),
			UserFilter::getUpdatedAtStart, validationUpdatedAt, actionsBlock);
		filterFieldsProcessor.register(updatedEnd, (f, v) -> f.setUpdatedAtEnd(toInstant(v)),
			UserFilter::getUpdatedAtEnd, validationUpdatedAt, actionsBlock);
	}

	@Override
	public void eventProcessor(Runnable onApply) {
		Runnable combinedOnApply = () -> {
			onApply.run();
			filterFieldsProcessor.refreshFilter();
			actionsBlock.onEventFilterChanged(filterFieldsProcessor.isFilterChanged());
		};

		actionsBlock.eventProcessor(() -> {
			if (!filterFieldsProcessor.validate()) {
				return;
			}
			filterFieldsProcessor.updateFilter();
			combinedOnApply.run();
		}, () -> {
			filterFieldsProcessor.clearFilter();
			combinedOnApply.run();
		});
	}

	public Component getIdBlock() {
		return createFilterBlock(idMin, idMax);
	}

	public Component getNameBlock() {
		return nameField;
	}

	public Component getEmailBlock() {
		return emailField;
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
		return actionsBlock.getActionBlock();
	}
}
