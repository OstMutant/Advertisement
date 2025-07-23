package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.FilterFieldsUtil.hasChanged;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.isValidDateRange;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.isValidNumberRange;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.toLong;
import static org.ost.advertisement.ui.utils.FilterHighlighterUtil.dehighlight;
import static org.ost.advertisement.ui.utils.FilterHighlighterUtil.highlight;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.toInstant;

import com.vaadin.flow.component.AbstractField;
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

	private final List<AbstractField<?, ?>> filterFields = List.of(titleField, categoryField, locationField,
		statusField, idMin, idMax, createdStart, createdEnd, updatedStart, updatedEnd);

	public AdvertisementFilterFields() {
		super(new AdvertisementFilter());
	}

	public void configure(Runnable onApply) {
		titleField.addValueChangeListener(e -> {
			newFilter.setTitleFilter(e.getValue());
			updateState();
		});
		categoryField.addValueChangeListener(e -> {
			newFilter.setCategoryFilter(e.getValue());
			updateState();
		});
		locationField.addValueChangeListener(e -> {
			newFilter.setLocationFilter(e.getValue());
			updateState();
		});
		statusField.addValueChangeListener(e -> {
			newFilter.setStatusFilter(e.getValue());
			updateState();
		});
		idMin.addValueChangeListener(e -> {
			newFilter.setStartId(toLong(e.getValue()));
			updateState();
		});
		idMax.addValueChangeListener(e -> {
			newFilter.setEndId(toLong(e.getValue()));
			updateState();
		});
		createdStart.addValueChangeListener(e -> {
			newFilter.setCreatedAtStart(toInstant(e.getValue()));
			updateState();
		});
		createdEnd.addValueChangeListener(e -> {
			newFilter.setCreatedAtEnd(toInstant(e.getValue()));
			updateState();
		});
		updatedStart.addValueChangeListener(e -> {
			newFilter.setUpdatedAtStart(toInstant(e.getValue()));
			updateState();
		});
		updatedEnd.addValueChangeListener(e -> {
			newFilter.setUpdatedAtEnd(toInstant(e.getValue()));
			updateState();
		});

		applyButton.addClickListener(e -> {
			if (!validate()) {
				return;
			}
			originalFilter.copyFrom(newFilter);
			updateState();
			onApply.run();
		});

		clearButton.addClickListener(e -> {
			clearAllFields();
			newFilter.clear();
			originalFilter.clear();
			updateState();
			onApply.run();
		});
	}

	@Override
	protected void clearAllFields() {
		clearAll(filterFields);
	}

	@Override
	protected void dehighlightFields() {
		dehighlight(filterFields);
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

	@Override
	protected boolean isFilterActive() {
		return validate() && hasChanged(newFilter, originalFilter);
	}

	@Override
	protected boolean validate() {
		return isValidNumberRange(newFilter.getStartId(), newFilter.getEndId())
			&& isValidDateRange(newFilter.getCreatedAtStart(), newFilter.getCreatedAtEnd())
			&& isValidDateRange(newFilter.getUpdatedAtStart(), newFilter.getUpdatedAtEnd());
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
