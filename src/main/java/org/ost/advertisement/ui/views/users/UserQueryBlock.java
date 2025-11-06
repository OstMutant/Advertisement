package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.constants.I18nKey.USER_FILTER_CREATED_END;
import static org.ost.advertisement.constants.I18nKey.USER_FILTER_CREATED_START;
import static org.ost.advertisement.constants.I18nKey.USER_FILTER_EMAIL_PLACEHOLDER;
import static org.ost.advertisement.constants.I18nKey.USER_FILTER_ID_MAX;
import static org.ost.advertisement.constants.I18nKey.USER_FILTER_ID_MIN;
import static org.ost.advertisement.constants.I18nKey.USER_FILTER_NAME_PLACEHOLDER;
import static org.ost.advertisement.constants.I18nKey.USER_FILTER_ROLE_ANY;
import static org.ost.advertisement.constants.I18nKey.USER_FILTER_UPDATED_END;
import static org.ost.advertisement.constants.I18nKey.USER_FILTER_UPDATED_START;
import static org.ost.advertisement.ui.views.components.ContentFactory.createCombo;
import static org.ost.advertisement.ui.views.components.ContentFactory.createDatePicker;
import static org.ost.advertisement.ui.views.components.ContentFactory.createFilterBlock;
import static org.ost.advertisement.ui.views.components.ContentFactory.createFullTextField;
import static org.ost.advertisement.ui.views.components.ContentFactory.createNumberField;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.mappers.filters.UserFilterMapper;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.ActionBlock;
import org.ost.advertisement.ui.views.components.filters.FilterFieldsProcessor;
import org.ost.advertisement.ui.views.components.sort.SortFieldsProcessor;
import org.ost.advertisement.ui.views.components.sort.TriStateSortIcon;

@SpringComponent
@UIScope
public class UserQueryBlock {

	private final ActionBlock actionsBlock;
	@Getter
	private final FilterFieldsProcessor<UserFilterDto> filterProcessor;
	@Getter
	private final SortFieldsProcessor sortProcessor;

	private final NumberField idMin;
	private final NumberField idMax;
	private final TextField nameField;
	private final TextField emailField;
	private final ComboBox<Role> roleCombo;
	private final DatePicker createdStart;
	private final DatePicker createdEnd;
	private final DatePicker updatedStart;
	private final DatePicker updatedEnd;

	@Getter
	private final TriStateSortIcon idSortIcon;
	@Getter
	private final TriStateSortIcon nameSortIcon;
	@Getter
	private final TriStateSortIcon emailSortIcon;
	@Getter
	private final TriStateSortIcon roleSortIcon;
	@Getter
	private final TriStateSortIcon createdSortIcon;
	@Getter
	private final TriStateSortIcon updatedSortIcon;

	public UserQueryBlock(UserFilterMapper filterMapper, ValidationService<UserFilterDto> validation,
						  I18nService i18n) {
		this.actionsBlock = new ActionBlock(i18n);
		this.filterProcessor = new FilterFieldsProcessor<>(filterMapper, validation, UserFilterDto.empty());
		this.sortProcessor = new SortFieldsProcessor(new CustomSort());

		this.idMin = createNumberField(i18n.get(USER_FILTER_ID_MIN));
		this.idMax = createNumberField(i18n.get(USER_FILTER_ID_MAX));
		this.nameField = createFullTextField(i18n.get(USER_FILTER_NAME_PLACEHOLDER));
		this.emailField = createFullTextField(i18n.get(USER_FILTER_EMAIL_PLACEHOLDER));
		this.roleCombo = createCombo(i18n.get(USER_FILTER_ROLE_ANY), Role.values());
		this.createdStart = createDatePicker(i18n.get(USER_FILTER_CREATED_START));
		this.createdEnd = createDatePicker(i18n.get(USER_FILTER_CREATED_END));
		this.updatedStart = createDatePicker(i18n.get(USER_FILTER_UPDATED_START));
		this.updatedEnd = createDatePicker(i18n.get(USER_FILTER_UPDATED_END));

		this.idSortIcon = new TriStateSortIcon();
		this.nameSortIcon = new TriStateSortIcon();
		this.emailSortIcon = new TriStateSortIcon();
		this.roleSortIcon = new TriStateSortIcon();
		this.createdSortIcon = new TriStateSortIcon();
		this.updatedSortIcon = new TriStateSortIcon();
	}

	@PostConstruct
	private void init() {
		sortProcessor.register(idSortIcon, User.Fields.id, actionsBlock);
		sortProcessor.register(nameSortIcon, User.Fields.name, actionsBlock);
		sortProcessor.register(emailSortIcon, User.Fields.email, actionsBlock);
		sortProcessor.register(roleSortIcon, User.Fields.role, actionsBlock);
		sortProcessor.register(createdSortIcon, User.Fields.createdAt, actionsBlock);
		sortProcessor.register(updatedSortIcon, User.Fields.updatedAt, actionsBlock);

		filterProcessor.register(idMin, UserFilterMeta.ID_MIN, actionsBlock);
		filterProcessor.register(idMax, UserFilterMeta.ID_MAX, actionsBlock);
		filterProcessor.register(nameField, UserFilterMeta.NAME, actionsBlock);
		filterProcessor.register(emailField, UserFilterMeta.EMAIL, actionsBlock);
		filterProcessor.register(roleCombo, UserFilterMeta.ROLE, actionsBlock);
		filterProcessor.register(createdStart, UserFilterMeta.CREATED_AT_START, actionsBlock);
		filterProcessor.register(createdEnd, UserFilterMeta.CREATED_AT_END, actionsBlock);
		filterProcessor.register(updatedStart, UserFilterMeta.UPDATED_AT_START, actionsBlock);
		filterProcessor.register(updatedEnd, UserFilterMeta.UPDATED_AT_END, actionsBlock);
	}

	public void eventProcessor(Runnable onApply) {
		actionsBlock.eventProcessor(() -> {
			if (!filterProcessor.validate()) {
				return;
			}
			filterProcessor.updateFilter();
			sortProcessor.updateSorting();
			onApply.run();
			filterProcessor.refreshFilter();
			sortProcessor.refreshSorting();
			actionsBlock.setChanged(false);
		}, () -> {
			filterProcessor.clearFilter();
			sortProcessor.clearSorting();
			onApply.run();
			filterProcessor.refreshFilter();
			sortProcessor.refreshSorting();
			actionsBlock.setChanged(false);
		});
	}

	public Component getIdFilter() {
		return createFilterBlock(idMin, idMax);
	}

	public Component getNameFilter() {
		return nameField;
	}

	public Component getEmailFilter() {
		return emailField;
	}

	public Component getRoleFilter() {
		return roleCombo;
	}

	public Component getCreatedFilter() {
		return createFilterBlock(createdStart, createdEnd);
	}

	public Component getUpdatedFilter() {
		return createFilterBlock(updatedStart, updatedEnd);
	}

	public Component getActionBlock() {
		return actionsBlock.getComponent();
	}
}
