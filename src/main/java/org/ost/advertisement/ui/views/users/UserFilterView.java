package org.ost.advertisement.ui.views.users;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.dto.UserFilter;

import jakarta.annotation.PostConstruct; // Import PostConstruct
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@SpringComponent
@UIScope
public class UserFilterView extends VerticalLayout {

	private TextField nameFilter;
	private NumberField idStartFilter;
	private NumberField idEndFilter;
	private DateTimePicker createdAtStartFilter;
	private DateTimePicker createdAtEndFilter;
	private DateTimePicker updatedAtStartFilter;
	private DateTimePicker updatedAtEndFilter;

	private Button clearFiltersButton;
	private Button loadUsersButton;

	private Runnable onFilterChange;

	public UserFilterView() {
		addClassName("user-filter-view");
		setPadding(true);
		setSpacing(true);

		createFilterFields();
		createFilterButtons();
		add(new HorizontalLayout(nameFilter, idStartFilter, idEndFilter),
			new HorizontalLayout(createdAtStartFilter, createdAtEndFilter),
			new HorizontalLayout(updatedAtStartFilter, updatedAtEndFilter),
			new HorizontalLayout(loadUsersButton, clearFiltersButton));
	}

	@PostConstruct // Added PostConstruct
	private void initLocales() {
		// Moved locale setting to PostConstruct
		createdAtStartFilter.setLocale(getLocale());
		createdAtEndFilter.setLocale(getLocale());
		updatedAtStartFilter.setLocale(getLocale());
		updatedAtEndFilter.setLocale(getLocale());
	}

	private void createFilterFields() {
		nameFilter = new TextField("Name");
		nameFilter.setPlaceholder("Filter by name...");
		nameFilter.setClearButtonVisible(true);

		idStartFilter = new NumberField("ID From");
		idStartFilter.setPlaceholder("Start ID");
		idStartFilter.setClearButtonVisible(true);
		idStartFilter.setMin(1);

		idEndFilter = new NumberField("ID To");
		idEndFilter.setPlaceholder("End ID");
		idEndFilter.setClearButtonVisible(true);
		idEndFilter.setMin(1);

		createdAtStartFilter = new DateTimePicker("Created From");
//		createdAtStartFilter.setClearButtonVisible(true);
//		createdAtStartFilter.setStep(null);

		createdAtEndFilter = new DateTimePicker("Created To");
//		createdAtEndFilter.setClearButtonVisible(true);
//		createdAtEndFilter.setStep(null);

		updatedAtStartFilter = new DateTimePicker("Updated From");
//		updatedAtStartFilter.setClearButtonVisible(true);
//		updatedAtStartFilter.setStep(null);

		updatedAtEndFilter = new DateTimePicker("Updated To");
//		updatedAtEndFilter.setClearButtonVisible(true);
//		updatedAtEndFilter.setStep(null);
	}

	private void createFilterButtons() {
		loadUsersButton = new Button("Load Users", VaadinIcon.SEARCH.create());
		loadUsersButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		loadUsersButton.addClickListener(event -> {
			if (onFilterChange != null) {
				onFilterChange.run();
			}
		});

		clearFiltersButton = new Button("Clear Filters", VaadinIcon.ERASER.create());
		clearFiltersButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
		clearFiltersButton.addClickListener(event -> {
			clearFilters();
			if (onFilterChange != null) {
				onFilterChange.run();
			}
		});
	}

	public UserFilter getUserFilter() {
		UserFilter filter = new UserFilter();
		filter.setNameFilter(nameFilter.getValue());

		if (idStartFilter.getValue() != null) {
			filter.setStartId(idStartFilter.getValue().longValue());
		}
		if (idEndFilter.getValue() != null) {
			filter.setEndId(idEndFilter.getValue().longValue());
		}

		if (createdAtStartFilter.getValue() != null) {
			filter.setCreatedAtStart(createdAtStartFilter.getValue().atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.SECONDS));
		}
		if (createdAtEndFilter.getValue() != null) {
			filter.setCreatedAtEnd(createdAtEndFilter.getValue().atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.SECONDS));
		}
		if (updatedAtStartFilter.getValue() != null) {
			filter.setUpdatedAtStart(updatedAtStartFilter.getValue().atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.SECONDS));
		}
		if (updatedAtEndFilter.getValue() != null) {
			filter.setUpdatedAtEnd(updatedAtEndFilter.getValue().atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.SECONDS));
		}
		return filter;
	}

	public void clearFilters() {
		nameFilter.clear();
		idStartFilter.clear();
		idEndFilter.clear();
		createdAtStartFilter.clear();
		createdAtEndFilter.clear();
		updatedAtStartFilter.clear();
		updatedAtEndFilter.clear();
	}

	public void setOnFilterChange(Runnable onFilterChange) {
		this.onFilterChange = onFilterChange;
	}
}
