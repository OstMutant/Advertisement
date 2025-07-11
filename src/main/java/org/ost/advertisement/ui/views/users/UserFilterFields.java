package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.ui.utils.FilterFieldsUtil.toLong;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.clearAll;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.createButton;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.createDatePicker;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.createFilterBlock;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.createNumberField;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.createTextField;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.isValidDateRange;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.isValidNumberRange;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.toInstant;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ConfigurableFilterDataProvider;
import lombok.Getter;
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.ui.utils.FilterHighlighterUtil;

public class UserFilterFields {

	private final UserFilter defaultFilter = new UserFilter();
	@Getter
	private final UserFilter userFilter = new UserFilter();

	private final NumberField idFilterMin = createNumberField("Min ID");
	private final NumberField idFilterMax = createNumberField("Max ID");
	private final TextField nameFilter = createTextField("Name...");
	private final DatePicker createdStart = createDatePicker("Created from");
	private final DatePicker createdEnd = createDatePicker("Created to");
	private final DatePicker updatedStart = createDatePicker("Updated from");
	private final DatePicker updatedEnd = createDatePicker("Updated to");
	private final Button applyFilterButton = createButton(VaadinIcon.FILTER, "Apply filters",
		ButtonVariant.LUMO_PRIMARY);
	private final Button clearFilterButton = createButton(VaadinIcon.ERASER, "Clear filters",
		ButtonVariant.LUMO_TERTIARY);

	public void configureFields(ConfigurableFilterDataProvider<User, Void, UserFilter> dataProvider) {
		idFilterMin.addValueChangeListener(e -> {
			userFilter.setStartId(toLong(e.getValue()));
			updateState();
		});
		idFilterMax.addValueChangeListener(e -> {
			userFilter.setEndId(toLong(e.getValue()));
			updateState();
		});
		nameFilter.addValueChangeListener(e -> {
			userFilter.setNameFilter(e.getValue());
			updateState();
		});
		createdStart.addValueChangeListener(e -> {
			userFilter.setCreatedAtStart(toInstant(e.getValue()));
			updateState();
		});
		createdEnd.addValueChangeListener(e -> {
			userFilter.setCreatedAtEnd(toInstant(e.getValue()));
			updateState();
		});
		updatedStart.addValueChangeListener(e -> {
			userFilter.setUpdatedAtStart(toInstant(e.getValue()));
			updateState();
		});
		updatedEnd.addValueChangeListener(e -> {
			userFilter.setUpdatedAtEnd(toInstant(e.getValue()));
			updateState();
		});

		applyFilterButton.addClickListener(e -> {
			if (!isValidNumberRange(userFilter.getStartId(), userFilter.getEndId()) ||
				!isValidDateRange(userFilter.getCreatedAtStart(), userFilter.getCreatedAtEnd()) ||
				!isValidDateRange(userFilter.getUpdatedAtStart(), userFilter.getUpdatedAtEnd())) {

				Notification.show("Неправильний діапазон фільтрів", 3000, Notification.Position.BOTTOM_START)
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
				applyFilterButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
				return;
			}

			applyFilterButton.removeThemeVariants(ButtonVariant.LUMO_ERROR);
			dataProvider.setFilter(userFilter);
			defaultFilter.copyFrom(userFilter);
			highlightChangedFilters(true);
		});

		clearFilterButton.addClickListener(e -> {
			clearAll(idFilterMin, idFilterMax, nameFilter, createdStart, createdEnd, updatedStart, updatedEnd);
			userFilter.clear();
			defaultFilter.clear();
			dataProvider.setFilter(userFilter);
			updateFilterButtonState();
			highlightChangedFilters(false);
		});
	}

	private void updateState() {
		updateFilterButtonState();
		highlightChangedFilters(true);
	}

	private void updateFilterButtonState() {
		applyFilterButton.removeThemeVariants(ButtonVariant.LUMO_CONTRAST);
		if (isFilterActive()) {
			applyFilterButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
		}
	}

	private boolean isFilterActive() {
		return userFilter.getNameFilter() != null && !userFilter.getNameFilter().isBlank()
			|| userFilter.getStartId() != null
			|| userFilter.getEndId() != null
			|| userFilter.getCreatedAtStart() != null
			|| userFilter.getCreatedAtEnd() != null
			|| userFilter.getUpdatedAtStart() != null
			|| userFilter.getUpdatedAtEnd() != null;
	}

	private void highlightChangedFilters(boolean enable) {
		if (!enable) {
			FilterHighlighterUtil.clearHighlight(nameFilter, idFilterMin, idFilterMax,
				createdStart, createdEnd, updatedStart, updatedEnd);
			return;
		}
		FilterHighlighterUtil.highlight(nameFilter, userFilter.getNameFilter(), defaultFilter.getNameFilter());
		FilterHighlighterUtil.highlight(idFilterMin, userFilter.getStartId(), defaultFilter.getStartId());
		FilterHighlighterUtil.highlight(idFilterMax, userFilter.getEndId(), defaultFilter.getEndId());
		FilterHighlighterUtil.highlight(createdStart, userFilter.getCreatedAtStart(),
			defaultFilter.getCreatedAtStart());
		FilterHighlighterUtil.highlight(createdEnd, userFilter.getCreatedAtEnd(), defaultFilter.getCreatedAtEnd());
		FilterHighlighterUtil.highlight(updatedStart, userFilter.getUpdatedAtStart(),
			defaultFilter.getUpdatedAtStart());
		FilterHighlighterUtil.highlight(updatedEnd, userFilter.getUpdatedAtEnd(), defaultFilter.getUpdatedAtEnd());
	}

	public Component getIdFilterBlock() {
		return createFilterBlock(idFilterMin, idFilterMax);
	}

	public Component getNameBlock() {
		return nameFilter;
	}

	public Component getCreatedBlock() {
		return createFilterBlock(createdStart, createdEnd);
	}

	public Component getUpdatedBlock() {
		return createFilterBlock(updatedStart, updatedEnd);
	}

	public Component getActionBlock() {
		HorizontalLayout actions = new HorizontalLayout(applyFilterButton, clearFilterButton);
		actions.setSpacing(false);
		actions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return actions;
	}
}
