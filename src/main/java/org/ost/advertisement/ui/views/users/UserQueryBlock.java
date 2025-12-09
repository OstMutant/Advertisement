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
import static org.ost.advertisement.ui.views.components.content.ContentFactory.createCombo;
import static org.ost.advertisement.ui.views.components.content.ContentFactory.createDatePicker;
import static org.ost.advertisement.ui.views.components.content.ContentFactory.createFilterBlock;
import static org.ost.advertisement.ui.views.components.content.ContentFactory.createFullTextField;
import static org.ost.advertisement.ui.views.components.content.ContentFactory.createNumberField;

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
import org.ost.advertisement.ui.views.components.query.filters.FilterFieldsProcessor;
import org.ost.advertisement.ui.views.components.query.QueryActionBlock;
import org.ost.advertisement.ui.views.components.query.QueryBlock;
import org.ost.advertisement.ui.views.components.query.sort.SortFieldsProcessor;
import org.ost.advertisement.ui.views.components.query.sort.TriStateSortIcon;
import org.ost.advertisement.ui.views.users.meta.UserFilterMeta;
import org.springframework.data.domain.Sort;

@SpringComponent
@UIScope
public class UserQueryBlock implements QueryBlock<UserFilterDto> {

	@Getter
	private final QueryActionBlock queryActionBlock;
	@Getter
	private final FilterFieldsProcessor<UserFilterDto> filterProcessor;
	@Getter
	private final SortFieldsProcessor sortProcessor;

	private final NumberField idMin;
	private final NumberField idMax;
	@Getter
	private final TextField nameField;
	@Getter
	private final TextField emailField;
	@Getter
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
						  I18nService i18n, QueryActionBlock queryActionBlock) {
		this.queryActionBlock = queryActionBlock;
		this.filterProcessor = new FilterFieldsProcessor<>(filterMapper, validation, UserFilterDto.empty());
		this.sortProcessor = new SortFieldsProcessor(new CustomSort(Sort.by(
			Sort.Order.desc(User.Fields.updatedAt),
			Sort.Order.desc(User.Fields.createdAt)
		)));

		this.idMin = createNumberField(i18n.get(USER_FILTER_ID_MIN));
		this.idMax = createNumberField(i18n.get(USER_FILTER_ID_MAX));
		this.nameField = createFullTextField(i18n.get(USER_FILTER_NAME_PLACEHOLDER));
		this.emailField = createFullTextField(i18n.get(USER_FILTER_EMAIL_PLACEHOLDER));
		this.roleCombo = createCombo(i18n.get(USER_FILTER_ROLE_ANY), Role.values());
		this.createdStart = createDatePicker(i18n.get(USER_FILTER_CREATED_START));
		this.createdEnd = createDatePicker(i18n.get(USER_FILTER_CREATED_END));
		this.updatedStart = createDatePicker(i18n.get(USER_FILTER_UPDATED_START));
		this.updatedEnd = createDatePicker(i18n.get(USER_FILTER_UPDATED_END));

		this.idSortIcon = new TriStateSortIcon(i18n);
		this.nameSortIcon = new TriStateSortIcon(i18n);
		this.emailSortIcon = new TriStateSortIcon(i18n);
		this.roleSortIcon = new TriStateSortIcon(i18n);
		this.createdSortIcon = new TriStateSortIcon(i18n);
		this.updatedSortIcon = new TriStateSortIcon(i18n);
	}

	@PostConstruct
	private void init() {
		sortProcessor.register(idSortIcon, User.Fields.id, queryActionBlock);
		sortProcessor.register(nameSortIcon, User.Fields.name, queryActionBlock);
		sortProcessor.register(emailSortIcon, User.Fields.email, queryActionBlock);
		sortProcessor.register(roleSortIcon, User.Fields.role, queryActionBlock);
		sortProcessor.register(createdSortIcon, User.Fields.createdAt, queryActionBlock);
		sortProcessor.register(updatedSortIcon, User.Fields.updatedAt, queryActionBlock);
		sortProcessor.refreshSorting();

		filterProcessor.register(idMin, UserFilterMeta.ID_MIN, queryActionBlock);
		filterProcessor.register(idMax, UserFilterMeta.ID_MAX, queryActionBlock);
		filterProcessor.register(nameField, UserFilterMeta.NAME, queryActionBlock);
		filterProcessor.register(emailField, UserFilterMeta.EMAIL, queryActionBlock);
		filterProcessor.register(roleCombo, UserFilterMeta.ROLE, queryActionBlock);
		filterProcessor.register(createdStart, UserFilterMeta.CREATED_AT_START, queryActionBlock);
		filterProcessor.register(createdEnd, UserFilterMeta.CREATED_AT_END, queryActionBlock);
		filterProcessor.register(updatedStart, UserFilterMeta.UPDATED_AT_START, queryActionBlock);
		filterProcessor.register(updatedEnd, UserFilterMeta.UPDATED_AT_END, queryActionBlock);
	}

	public Component getIdFilter() {
		return createFilterBlock(idMin, idMax);
	}

	public Component getCreatedFilter() {
		return createFilterBlock(createdStart, createdEnd);
	}

	public Component getUpdatedFilter() {
		return createFilterBlock(updatedStart, updatedEnd);
	}
}
