package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.toInstant;
import static org.ost.advertisement.utils.FilterUtil.isValidDateRange;
import static org.ost.advertisement.utils.FilterUtil.isValidNumberRange;
import static org.ost.advertisement.utils.FilterUtil.toLong;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import java.util.function.Predicate;
import org.ost.advertisement.dto.AdvertisementFilter;
import org.ost.advertisement.ui.views.filters.AbstractFilterFields;

public class AdvertisementFilterFields extends AbstractFilterFields<AdvertisementFilter> {

	private final TextField title = createFullTextField("Title...");

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
		register(title, (f, v) -> f.setTitle(v == null ? null : v.isBlank() ? null : v), AdvertisementFilter::getTitle,
			f -> true);

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
		return title;
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

	public Component getActionBlock() {
		HorizontalLayout actions = new HorizontalLayout(applyButton, clearButton);
		actions.setSpacing(false);
		actions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return actions;
	}
}
