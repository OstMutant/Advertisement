package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.toInstant;
import static org.ost.advertisement.utils.FilterUtil.isValidDateRange;
import static org.ost.advertisement.utils.FilterUtil.isValidNumberRange;
import static org.ost.advertisement.utils.FilterUtil.toLong;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import java.util.List;
import java.util.function.Predicate;
import org.ost.advertisement.dto.AdvertisementFilter;
import org.ost.advertisement.ui.views.filters.AbstractFilterFields;

public class AdvertisementFilterFields extends AbstractFilterFields<AdvertisementFilter> {

	private final TextField titleField = createFullTextField("Title...");
	private final TextField categoryField = createShortTextField("Category...");
	private final TextField locationField = createShortTextField("Location...");
	private final Select<String> statusField = createSelect("Status", List.of("ACTIVE", "EXPIRED", "DRAFT", "SOLD"));

	private final NumberField idMin = createNumberField("Min ID");
	private final NumberField idMax = createNumberField("Max ID");

	private final DatePicker createdStart = createDatePicker("Created from");
	private final DatePicker createdEnd = createDatePicker("Created to");
	private final DatePicker updatedStart = createDatePicker("Updated from");
	private final DatePicker updatedEnd = createDatePicker("Updated to");

	public AdvertisementFilterFields() {
		super(new AdvertisementFilter());
	}

	@Override
	public void configure(Runnable onApply) {
		super.configure(onApply);
		register(titleField, AdvertisementFilter::setTitleFilter, AdvertisementFilter::getTitleFilter, f -> true);
		register(categoryField, AdvertisementFilter::setCategoryFilter, AdvertisementFilter::getCategoryFilter,
			f -> true);
		register(locationField, AdvertisementFilter::setLocationFilter, AdvertisementFilter::getLocationFilter,
			f -> true);
		register(statusField, AdvertisementFilter::setStatusFilter, AdvertisementFilter::getStatusFilter, f -> true);

		Predicate<AdvertisementFilter> validationId = f -> isValidNumberRange(f.getStartId(), f.getEndId());
		register(idMin, (f, v) -> f.setStartId(toLong(v)), AdvertisementFilter::getStartId, validationId);
		register(idMax, (f, v) -> f.setEndId(toLong(v)), AdvertisementFilter::getEndId, validationId);

		Predicate<AdvertisementFilter> validationCreatedAt = f -> isValidDateRange(f.getCreatedAtStart(),
			f.getCreatedAtEnd());
		register(createdStart, (f, v) -> f.setCreatedAtStart(toInstant(v)), AdvertisementFilter::getCreatedAtStart,
			validationCreatedAt);
		register(createdEnd, (f, v) -> f.setCreatedAtEnd(toInstant(v)), AdvertisementFilter::getCreatedAtEnd,
			validationCreatedAt);

		Predicate<AdvertisementFilter> validationUpdatedAt = f -> isValidDateRange(f.getUpdatedAtStart(),
			f.getUpdatedAtEnd());
		register(updatedStart, (f, v) -> f.setUpdatedAtStart(toInstant(v)), AdvertisementFilter::getUpdatedAtStart,
			validationUpdatedAt);
		register(updatedEnd, (f, v) -> f.setUpdatedAtEnd(toInstant(v)), AdvertisementFilter::getUpdatedAtEnd,
			validationUpdatedAt);
	}

	public Component getTitleBlock() {
		return titleField;
	}

	public Component getCategoryBlock() {
		return categoryField;
	}

	public Component getLocationBlock() {
		return locationField;
	}

	public Component getStatusBlock() {
		return statusField;
	}

	public Component getIdBlock() {
		return createFilterBlock(idMin, idMax);
	}

	public Component getCreatedBlock() {
		return createFilterBlock(createdStart, createdEnd);
	}

	public Component getUpdatedBlock() {
		return createFilterBlock(updatedStart, updatedEnd);
	}

	public HorizontalLayout getActionBlock() {
		HorizontalLayout actions = new HorizontalLayout(applyButton, clearButton);
		actions.setSpacing(false);
		actions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return actions;
	}
}
