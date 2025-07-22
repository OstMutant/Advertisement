package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.ui.utils.FilterFieldsUtil.isValidDateRange;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.isValidNumberRange;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.toInstant;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.toLong;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.Role;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.ui.utils.FilterHighlighterUtil;
import org.ost.advertisement.ui.views.filters.AbstractFilterFields;

public class UserFilterFields extends AbstractFilterFields<User, UserFilter> {

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
		applyButton = createButton(VaadinIcon.FILTER, "Apply filters", ButtonVariant.LUMO_PRIMARY);
		clearButton = createButton(VaadinIcon.ERASER, "Clear filters", ButtonVariant.LUMO_TERTIARY);
	}

	public void configure(Runnable onApply) {
		idMin.addValueChangeListener(e -> {
			filter.setStartId(toLong(e.getValue()));
			updateState();
		});
		idMax.addValueChangeListener(e -> {
			filter.setEndId(toLong(e.getValue()));
			updateState();
		});
		nameField.addValueChangeListener(e -> {
			filter.setNameFilter(e.getValue());
			updateState();
		});
		roleCombo.addValueChangeListener(e -> {
			filter.setRole(e.getValue());
			updateState();
		});
		createdStart.addValueChangeListener(e -> {
			filter.setCreatedAtStart(toInstant(e.getValue()));
			updateState();
		});
		createdEnd.addValueChangeListener(e -> {
			filter.setCreatedAtEnd(toInstant(e.getValue()));
			updateState();
		});
		updatedStart.addValueChangeListener(e -> {
			filter.setUpdatedAtStart(toInstant(e.getValue()));
			updateState();
		});
		updatedEnd.addValueChangeListener(e -> {
			filter.setUpdatedAtEnd(toInstant(e.getValue()));
			updateState();
		});

		applyButton.addClickListener(e -> {
			highlightChangedFields(false);
			onApply.run();
		});
		clearButton.addClickListener(e -> {
			clearAllFields();
			filter.clear();
			highlightChangedFields(false);
			onApply.run();
		});
	}

	@Override
	protected UserFilter cloneFilter(UserFilter original) {
		UserFilter clone = new UserFilter();
		clone.copyFrom(original);
		return clone;
	}

	@Override
	protected void clearAllFields() {
		clearAll(idMin, idMax, nameField, roleCombo, createdStart, createdEnd, updatedStart, updatedEnd);
	}

	@Override
	protected void highlightChangedFields(boolean enable) {
		if (!enable) {
			FilterHighlighterUtil.clearHighlight(idMin, idMax, nameField, roleCombo, createdStart, createdEnd, updatedStart,
				updatedEnd);
			return;
		}
		FilterHighlighterUtil.highlight(idMin, filter.getStartId(), defaultFilter.getStartId());
		FilterHighlighterUtil.highlight(idMax, filter.getEndId(), defaultFilter.getEndId());
		FilterHighlighterUtil.highlight(nameField, filter.getNameFilter(), defaultFilter.getNameFilter());
		FilterHighlighterUtil.highlight(roleCombo, filter.getRole(), defaultFilter.getRole());
		FilterHighlighterUtil.highlight(createdStart, filter.getCreatedAtStart(), defaultFilter.getCreatedAtStart());
		FilterHighlighterUtil.highlight(createdEnd, filter.getCreatedAtEnd(), defaultFilter.getCreatedAtEnd());
		FilterHighlighterUtil.highlight(updatedStart, filter.getUpdatedAtStart(), defaultFilter.getUpdatedAtStart());
		FilterHighlighterUtil.highlight(updatedEnd, filter.getUpdatedAtEnd(), defaultFilter.getUpdatedAtEnd());
	}

	@Override
	protected boolean isFilterActive() {
		return filter.getNameFilter() != null && !filter.getNameFilter().isBlank()
			|| filter.getRole() != null
			|| filter.getStartId() != null || filter.getEndId() != null
			|| filter.getCreatedAtStart() != null || filter.getCreatedAtEnd() != null
			|| filter.getUpdatedAtStart() != null || filter.getUpdatedAtEnd() != null;
	}

	@Override
	protected boolean validate() {
		return isValidNumberRange(filter.getStartId(), filter.getEndId())
			&& isValidDateRange(filter.getCreatedAtStart(), filter.getCreatedAtEnd())
			&& isValidDateRange(filter.getUpdatedAtStart(), filter.getUpdatedAtEnd());
	}

	@Override
	protected void copyFilter(UserFilter source, UserFilter target) {
		target.copyFrom(source);
	}

	@Override
	protected void clearFilter(UserFilter target) {
		target.clear();
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

	public Component getCreatedBlock() {
		return createFilterBlock(createdStart, createdEnd);
	}

	public Component getUpdatedBlock() {
		return createFilterBlock(updatedStart, updatedEnd);
	}

	public Component getActionBlock() {
		HorizontalLayout actions = new HorizontalLayout(applyButton, clearButton);
		actions.setSpacing(false);
		actions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return actions;
	}
}
