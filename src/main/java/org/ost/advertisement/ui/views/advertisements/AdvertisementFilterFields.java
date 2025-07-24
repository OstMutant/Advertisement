package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.FilterHighlighterUtil.highlight;
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

	public void configure(Runnable onApply) {
		super.configure(onApply);
		register(titleField, newFilter::setTitleFilter);
		register(categoryField, newFilter::setCategoryFilter);
		register(locationField, newFilter::setLocationFilter);
		register(statusField, newFilter::setStatusFilter);
		register(idMin, v -> newFilter.setStartId(toLong(v)));
		register(idMax, v -> newFilter.setEndId(toLong(v)));
		register(createdStart, v -> newFilter.setCreatedAtStart(toInstant(v)));
		register(createdEnd, v -> newFilter.setCreatedAtEnd(toInstant(v)));
		register(updatedStart, v -> newFilter.setUpdatedAtStart(toInstant(v)));
		register(updatedEnd, v -> newFilter.setUpdatedAtEnd(toInstant(v)));
	}

	@Override
	protected void highlightChangedFields() {
		boolean isIdValid = isValidNumberRange(newFilter.getStartId(), newFilter.getEndId());
		highlight(idMin, newFilter.getStartId(), originalFilter.getStartId(), defaultFilter.getStartId(), isIdValid);
		highlight(idMax, newFilter.getEndId(), originalFilter.getEndId(), defaultFilter.getEndId(), isIdValid);

		highlight(titleField, newFilter.getTitleFilter(), originalFilter.getTitleFilter(),
			defaultFilter.getTitleFilter());
		highlight(categoryField, newFilter.getCategoryFilter(), originalFilter.getCategoryFilter(),
			defaultFilter.getCategoryFilter());
		highlight(locationField, newFilter.getLocationFilter(), originalFilter.getLocationFilter(),
			defaultFilter.getLocationFilter());

		boolean isCreatedAtValid = isValidDateRange(newFilter.getCreatedAtStart(), newFilter.getCreatedAtEnd());
		highlight(createdStart, newFilter.getCreatedAtStart(), originalFilter.getCreatedAtStart(),
			defaultFilter.getCreatedAtStart(), isCreatedAtValid);
		highlight(createdEnd, newFilter.getCreatedAtEnd(), originalFilter.getCreatedAtEnd(),
			defaultFilter.getCreatedAtEnd(), isCreatedAtValid);

		boolean isUpdatedAtValid = isValidDateRange(newFilter.getUpdatedAtStart(), newFilter.getUpdatedAtEnd());
		highlight(updatedStart, newFilter.getUpdatedAtStart(), originalFilter.getUpdatedAtStart(),
			defaultFilter.getUpdatedAtStart(), isUpdatedAtValid);
		highlight(updatedEnd, newFilter.getUpdatedAtEnd(), originalFilter.getUpdatedAtEnd(),
			defaultFilter.getUpdatedAtEnd(), isUpdatedAtValid);
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
