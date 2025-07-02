package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.ost.advertisement.dto.AdvertisementFilter;


@SpringComponent
@UIScope
public class AdvertisementFilterView extends VerticalLayout {

	private TextField titleFilter;
	private TextField categoryFilter;
	private TextField locationFilter;
	private Select<String> statusFilter;
	private NumberField idStartFilter;
	private NumberField idEndFilter;
	private DateTimePicker createdAtStartFilter;
	private DateTimePicker createdAtEndFilter;
	private DateTimePicker updatedAtStartFilter;
	private DateTimePicker updatedAtEndFilter;

	private Button clearFiltersButton;
	private Button loadAdvertisementsButton;

	private Runnable onFilterChange;

	public AdvertisementFilterView() {
		addClassName("advertisement-filter-view");
		setPadding(true);
		setSpacing(true);

		createFilterFields();
		createFilterButtons();
		add(new HorizontalLayout(titleFilter, categoryFilter, locationFilter, statusFilter),
			new HorizontalLayout(idStartFilter, idEndFilter),
			new HorizontalLayout(createdAtStartFilter, createdAtEndFilter),
			new HorizontalLayout(updatedAtStartFilter, updatedAtEndFilter),
			new HorizontalLayout(loadAdvertisementsButton, clearFiltersButton));
	}

	private void createFilterFields() {
		titleFilter = new TextField("Title");
		titleFilter.setPlaceholder("Filter by title...");
		titleFilter.setClearButtonVisible(true);

		categoryFilter = new TextField("Category");
		categoryFilter.setPlaceholder("Filter by category...");
		categoryFilter.setClearButtonVisible(true);

		locationFilter = new TextField("Location");
		locationFilter.setPlaceholder("Filter by location...");
		locationFilter.setClearButtonVisible(true);

		statusFilter = new Select<>();
		statusFilter.setLabel("Status");
		statusFilter.setItems("ACTIVE", "EXPIRED", "DRAFT", "SOLD");
		statusFilter.setPlaceholder("Filter by status...");
//		statusFilter.setClearButtonVisible(true);

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
		createdAtStartFilter.setLocale(getLocale());
		createdAtStartFilter.setStep(null);

		createdAtEndFilter = new DateTimePicker("Created To");
//		createdAtEndFilter.setClearButtonVisible(true);
		createdAtEndFilter.setLocale(getLocale());
		createdAtEndFilter.setStep(null);

		updatedAtStartFilter = new DateTimePicker("Updated From");
//		updatedAtStartFilter.setClearButtonVisible(true);
		updatedAtStartFilter.setLocale(getLocale());
		updatedAtStartFilter.setStep(null);

		updatedAtEndFilter = new DateTimePicker("Updated To");
//		updatedAtEndFilter.setClearButtonVisible(true);
		updatedAtEndFilter.setLocale(getLocale());
		updatedAtEndFilter.setStep(null);
	}

	private void createFilterButtons() {
		loadAdvertisementsButton = new Button("Load Advertisements", VaadinIcon.SEARCH.create());
		loadAdvertisementsButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		loadAdvertisementsButton.addClickListener(event -> {
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

	public AdvertisementFilter getAdvertisementFilter() {
		AdvertisementFilter filter = new AdvertisementFilter();
		filter.setTitleFilter(titleFilter.getValue());
		filter.setCategoryFilter(categoryFilter.getValue());
		filter.setLocationFilter(locationFilter.getValue());
		filter.setStatusFilter(statusFilter.getValue());

		if (idStartFilter.getValue() != null) {
			filter.setStartId(idStartFilter.getValue().longValue());
		}
		if (idEndFilter.getValue() != null) {
			filter.setEndId(idEndFilter.getValue().longValue());
		}

		if (createdAtStartFilter.getValue() != null) {
			filter.setCreatedAtStart(createdAtStartFilter.getValue().atZone(ZoneId.systemDefault()).toInstant()
				.truncatedTo(ChronoUnit.SECONDS));
		}
		if (createdAtEndFilter.getValue() != null) {
			filter.setCreatedAtEnd(createdAtEndFilter.getValue().atZone(ZoneId.systemDefault()).toInstant()
				.truncatedTo(ChronoUnit.SECONDS));
		}
		if (updatedAtStartFilter.getValue() != null) {
			filter.setUpdatedAtStart(updatedAtStartFilter.getValue().atZone(ZoneId.systemDefault()).toInstant()
				.truncatedTo(ChronoUnit.SECONDS));
		}
		if (updatedAtEndFilter.getValue() != null) {
			filter.setUpdatedAtEnd(updatedAtEndFilter.getValue().atZone(ZoneId.systemDefault()).toInstant()
				.truncatedTo(ChronoUnit.SECONDS));
		}
		return filter;
	}

	public void clearFilters() {
		titleFilter.clear();
		categoryFilter.clear();
		locationFilter.clear();
		statusFilter.clear();
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
