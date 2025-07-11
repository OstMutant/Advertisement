package org.ost.advertisement.ui.views.users;

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
import com.vaadin.flow.data.value.ValueChangeMode;
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

	private NumberField idFilterMin;
	private NumberField idFilterMax;
	private TextField nameFilter;
	private DatePicker createdStart;
	private DatePicker createdEnd;
	private DatePicker updatedStart;
	private DatePicker updatedEnd;
	private Button applyFilterButton;
	private Button clearFilterButton;

	public UserFilterFields() {
		idFilterMin = createNumberField("Min ID");
		idFilterMax = createNumberField("Max ID");
		nameFilter = createTextField("Name...");
		createdStart = createDatePicker("Created from");
		createdEnd = createDatePicker("Created to");
		updatedStart = createDatePicker("Updated from");
		updatedEnd = createDatePicker("Updated to");

		applyFilterButton = new Button(VaadinIcon.FILTER.create());
		applyFilterButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_PRIMARY);
		applyFilterButton.getElement().setProperty("title", "Apply filters");

		clearFilterButton = new Button(VaadinIcon.ERASER.create());
		clearFilterButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
		clearFilterButton.getElement().setProperty("title", "Clear filters");
	}

	private NumberField createNumberField(String placeholder) {
		NumberField field = new NumberField();
		field.setWidth("100px");
		field.setClearButtonVisible(true);
		field.setPlaceholder(placeholder);
		field.setValueChangeMode(ValueChangeMode.EAGER);
		return field;
	}

	private TextField createTextField(String placeholder) {
		TextField field = new TextField();
		field.setWidthFull();
		field.setPlaceholder(placeholder);
		field.setClearButtonVisible(true);
		field.setValueChangeMode(ValueChangeMode.EAGER);
		return field;
	}

	private DatePicker createDatePicker(String placeholder) {
		DatePicker field = new DatePicker();
		field.setWidth("140px");
		field.setPlaceholder(placeholder);
		return field;
	}

	public void configureFields(ConfigurableFilterDataProvider<User, Void, UserFilter> dataProvider) {
		idFilterMin.addValueChangeListener(e -> {
			userFilter.setStartId(toLong(e.getValue()));
			updateState(dataProvider);
		});
		idFilterMax.addValueChangeListener(e -> {
			userFilter.setEndId(toLong(e.getValue()));
			updateState(dataProvider);
		});
		nameFilter.addValueChangeListener(e -> {
			userFilter.setNameFilter(e.getValue());
			updateState(dataProvider);
		});
		createdStart.addValueChangeListener(e -> {
			userFilter.setCreatedAtStart(toInstant(e.getValue()));
			updateState(dataProvider);
		});
		createdEnd.addValueChangeListener(e -> {
			userFilter.setCreatedAtEnd(toInstant(e.getValue()));
			updateState(dataProvider);
		});
		updatedStart.addValueChangeListener(e -> {
			userFilter.setUpdatedAtStart(toInstant(e.getValue()));
			updateState(dataProvider);
		});
		updatedEnd.addValueChangeListener(e -> {
			userFilter.setUpdatedAtEnd(toInstant(e.getValue()));
			updateState(dataProvider);
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
			defaultFilter.setEndId(userFilter.getEndId());
			defaultFilter.setCreatedAtEnd(userFilter.getCreatedAtEnd());
			defaultFilter.setUpdatedAtEnd(userFilter.getUpdatedAtEnd());
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

	private void updateState(ConfigurableFilterDataProvider<User, Void, UserFilter> dataProvider) {
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
		idFilterMin.clear();
		idFilterMax.clear();
		nameFilter.clear();
		createdStart.clear();
		createdEnd.clear();
		updatedStart.clear();
		updatedEnd.clear();
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
		FilterHighlighterUtil.highlight(createdStart, userFilter.getCreatedAtStart(), defaultFilter.getCreatedAtStart());
		FilterHighlighterUtil.highlight(createdEnd, userFilter.getCreatedAtEnd(), defaultFilter.getCreatedAtEnd());
		FilterHighlighterUtil.highlight(updatedStart, userFilter.getUpdatedAtStart(), defaultFilter.getUpdatedAtStart());
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


