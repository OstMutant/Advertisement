package org.ost.advertisement.ui.views.users;

import static org.ost.advertisement.ui.utils.FilterFieldsUtil.clearAll;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.createDatePicker;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.createNumberField;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.createTextField;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ConfigurableFilterDataProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.Getter;
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.ui.utils.FilterHighlighterUtil;

public class UserFilterFields {

	private final UserFilter defaultFilter = new UserFilter();
	@Getter
	private final UserFilter userFilter = new UserFilter();

	private NumberField idFilterMin = createNumberField("Min ID");
	private NumberField idFilterMax = createNumberField("Max ID");
	private TextField nameFilter = createTextField("Name...");
	private DatePicker createdStart = createDatePicker("Created from");
	private DatePicker createdEnd = createDatePicker("Created to");
	private DatePicker updatedStart = createDatePicker("Updated from");
	private DatePicker updatedEnd = createDatePicker("Updated to");
	private Button applyFilterButton;
	private Button clearFilterButton;

	public UserFilterFields() {

		applyFilterButton = new Button(VaadinIcon.FILTER.create());
		applyFilterButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_PRIMARY);
		applyFilterButton.getElement().setProperty("title", "Apply filters");

		clearFilterButton = new Button(VaadinIcon.ERASER.create());
		clearFilterButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
		clearFilterButton.getElement().setProperty("title", "Clear filters");
	}

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
			if (!isValidRange(userFilter.getStartId(), userFilter.getEndId())
				|| !isValidRange(userFilter.getCreatedAtStart(), userFilter.getCreatedAtEnd())
				|| !isValidRange(userFilter.getUpdatedAtStart(), userFilter.getUpdatedAtEnd())) {

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
			clearFilterFields();
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

	private void clearFilterFields() {
		clearAll(idFilterMin, idFilterMax, nameFilter, createdStart, createdEnd, updatedStart, updatedEnd);
		userFilter.clear();
		applyFilterButton.removeThemeVariants(ButtonVariant.LUMO_ERROR);
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

	private Instant toInstant(LocalDate date) {
		return date != null ? date.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
	}

	private Long toLong(Double value) {
		return value != null ? value.longValue() : null;
	}

	private boolean isValidRange(Long min, Long max) {
		return min == null || max == null || min <= max;
	}

	private boolean isValidRange(Instant start, Instant end) {
		return start == null || end == null || !start.isAfter(end);
	}

	public Component getIdFilterBlock() {
		VerticalLayout layout = new VerticalLayout(idFilterMin, idFilterMax);
		layout.setPadding(false);
		layout.setSpacing(false);
		return layout;
	}

	public Component getNameBlock() {
		return nameFilter;
	}

	public Component getCreatedBlock() {
		VerticalLayout layout = new VerticalLayout(createdStart, createdEnd);
		layout.setPadding(false);
		layout.setSpacing(false);
		return layout;
	}

	public Component getUpdatedBlock() {
		VerticalLayout layout = new VerticalLayout(updatedStart, updatedEnd);
		layout.setPadding(false);
		layout.setSpacing(false);
		return layout;
	}

	public Component getActionBlock() {
		HorizontalLayout actions = new HorizontalLayout(applyFilterButton, clearFilterButton);
		actions.setSpacing(false);
		actions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return actions;
	}
}


