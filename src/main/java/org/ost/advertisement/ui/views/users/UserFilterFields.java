package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ConfigurableFilterDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.time.ZoneId;
import lombok.Getter;
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.ui.utils.FilterHighlighterUtil;

@Getter
public class UserFilterFields {

	private final UserFilter defaultFilter = new UserFilter();
	private final UserFilter userFilter = new UserFilter();

	private NumberField idFilter;
	private TextField nameFilter;
	private DatePicker createdStart;
	private DatePicker updatedStart;
	private Button applyFilterButton;
	private Button clearFilterButton;

	public UserFilterFields() {
		idFilter = new NumberField();
		idFilter.setWidth("100px");
		idFilter.setClearButtonVisible(true);
		idFilter.setPlaceholder("Min ID");
		idFilter.setValueChangeMode(ValueChangeMode.EAGER);

		nameFilter = new TextField();
		nameFilter.setWidthFull();
		nameFilter.setPlaceholder("Name...");
		nameFilter.setClearButtonVisible(true);
		nameFilter.setValueChangeMode(ValueChangeMode.EAGER);

		createdStart = new DatePicker();
		createdStart.setWidth("140px");
		createdStart.setPlaceholder("From");

		updatedStart = new DatePicker();
		updatedStart.setWidth("140px");
		updatedStart.setPlaceholder("From");

		applyFilterButton = new Button(VaadinIcon.FILTER.create());
		applyFilterButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_PRIMARY);
		applyFilterButton.getElement().setProperty("title", "Apply filters");

		clearFilterButton = new Button(VaadinIcon.ERASER.create());
		clearFilterButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
		clearFilterButton.getElement().setProperty("title", "Clear filters");

		HorizontalLayout actionsHeader = new HorizontalLayout(applyFilterButton, clearFilterButton);
		actionsHeader.setSpacing(false);
		actionsHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

	}

	public void configureFields(ConfigurableFilterDataProvider<User, Void, UserFilter> dataProvider) {
		idFilter.addValueChangeListener(e -> {
			userFilter.setStartId(e.getValue() != null ? e.getValue().longValue() : null);
			updateFilterButtonState();
			highlightChangedFilters(true);
		});

		nameFilter.addValueChangeListener(e -> {
			userFilter.setNameFilter(e.getValue());
			updateFilterButtonState();
			highlightChangedFilters(true);
		});

		createdStart.addValueChangeListener(e -> {
			userFilter.setCreatedAtStart(e.getValue() != null ?
				e.getValue().atStartOfDay(ZoneId.systemDefault()).toInstant() : null);
			updateFilterButtonState();
			highlightChangedFilters(true);
		});

		updatedStart.addValueChangeListener(e -> {
			userFilter.setUpdatedAtStart(e.getValue() != null ?
				e.getValue().atStartOfDay(ZoneId.systemDefault()).toInstant() : null);
			updateFilterButtonState();
			highlightChangedFilters(true);
		});

		applyFilterButton.addClickListener(e -> {
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

	private void highlightChangedFilters(boolean enable) {
		if (!enable) {
			FilterHighlighterUtil.clearHighlight(nameFilter, idFilter, createdStart, updatedStart);
			return;
		}
		FilterHighlighterUtil.highlight(nameFilter, userFilter.getNameFilter(), defaultFilter.getNameFilter());
		FilterHighlighterUtil.highlight(idFilter, userFilter.getStartId(), defaultFilter.getStartId());
		FilterHighlighterUtil.highlight(createdStart, userFilter.getCreatedAtStart(),
			defaultFilter.getCreatedAtStart());
		FilterHighlighterUtil.highlight(updatedStart, userFilter.getUpdatedAtStart(),
			defaultFilter.getUpdatedAtStart());
	}

	private void updateFilterButtonState() {
		if (isFilterActive()) {
			applyFilterButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
		} else {
			applyFilterButton.removeThemeVariants(ButtonVariant.LUMO_CONTRAST);
		}
	}

	private boolean isFilterActive() {
		return userFilter.getNameFilter() != null && !userFilter.getNameFilter().isBlank()
			|| userFilter.getStartId() != null
			|| userFilter.getCreatedAtStart() != null
			|| userFilter.getUpdatedAtStart() != null;
	}

	private void clearFilterFields() {
		idFilter.clear();
		nameFilter.clear();
		createdStart.clear();
		updatedStart.clear();
		userFilter.clear();
	}
}
