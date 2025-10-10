package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.constans.I18nKey.USER_FILTER_CREATED_END;
import static org.ost.advertisement.constans.I18nKey.USER_FILTER_CREATED_START;
import static org.ost.advertisement.constans.I18nKey.USER_FILTER_EMAIL_PLACEHOLDER;
import static org.ost.advertisement.constans.I18nKey.USER_FILTER_ID_MAX;
import static org.ost.advertisement.constans.I18nKey.USER_FILTER_ID_MIN;
import static org.ost.advertisement.constans.I18nKey.USER_FILTER_NAME_PLACEHOLDER;
import static org.ost.advertisement.constans.I18nKey.USER_FILTER_ROLE_ANY;
import static org.ost.advertisement.constans.I18nKey.USER_FILTER_UPDATED_END;
import static org.ost.advertisement.constans.I18nKey.USER_FILTER_UPDATED_START;
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
import org.ost.advertisement.mappers.filters.UserFilterMapper;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.filters.AbstractFilterFields;
import org.ost.advertisement.ui.views.components.filters.FilterActionsBlock;

@SpringComponent
@UIScope
public class UserFilterFields extends AbstractFilterFields<UserFilter> {

	private final NumberField idMin;
	private final NumberField idMax;
	private final TextField nameField;
	private final TextField emailField;
	private final ComboBox<Role> roleCombo;
	private final DatePicker createdStart;
	private final DatePicker createdEnd;
	private final DatePicker updatedStart;
	private final DatePicker updatedEnd;
	private final FilterActionsBlock actionsBlock;

	public UserFilterFields(UserFilterMapper filterMapper, ValidationService<UserFilter> validation, I18nService i18n) {
		super(UserFilter.empty(), validation, filterMapper);

		this.idMin = createNumberField(i18n.get(USER_FILTER_ID_MIN));
		this.idMax = createNumberField(i18n.get(USER_FILTER_ID_MAX));
		this.nameField = createFullTextField(i18n.get(USER_FILTER_NAME_PLACEHOLDER));
		this.emailField = createFullTextField(i18n.get(USER_FILTER_EMAIL_PLACEHOLDER));
		this.roleCombo = createCombo(i18n.get(USER_FILTER_ROLE_ANY), Role.values());
		this.createdStart = createDatePicker(i18n.get(USER_FILTER_CREATED_START));
		this.createdEnd = createDatePicker(i18n.get(USER_FILTER_CREATED_END));
		this.updatedStart = createDatePicker(i18n.get(USER_FILTER_UPDATED_START));
		this.updatedEnd = createDatePicker(i18n.get(USER_FILTER_UPDATED_END));
		this.actionsBlock = new FilterActionsBlock(i18n);
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

		Predicate<UserFilter> validationUpdatedAt = f -> isValidProperty(f, "updatedAtStart") && isValidProperty(f,
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

