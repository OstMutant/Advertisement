package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.FilterFieldsUtil.isValidDateRange;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.isValidNumberRange;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.toInstant;
import static org.ost.advertisement.ui.utils.FilterFieldsUtil.toLong;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import java.util.List;
import org.ost.advertisement.dto.AdvertisementFilter;
import org.ost.advertisement.entyties.Advertisement;
import org.ost.advertisement.ui.utils.FilterHighlighterUtil;
import org.ost.advertisement.ui.views.filters.AbstractFilterFields;

public class AdvertisementFilterFields extends AbstractFilterFields<Advertisement, AdvertisementFilter> {

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
		applyButton = createButton(VaadinIcon.FILTER, "Apply filters", ButtonVariant.LUMO_PRIMARY);
		clearButton = createButton(VaadinIcon.ERASER, "Clear filters", ButtonVariant.LUMO_TERTIARY);
	}

	public void configure(Runnable onApply) {
		titleField.addValueChangeListener(e -> {
			filter.setTitleFilter(e.getValue());
			updateState();
		});
		categoryField.addValueChangeListener(e -> {
			filter.setCategoryFilter(e.getValue());
			updateState();
		});
		locationField.addValueChangeListener(e -> {
			filter.setLocationFilter(e.getValue());
			updateState();
		});
		statusField.addValueChangeListener(e -> {
			filter.setStatusFilter(e.getValue());
			updateState();
		});
		idMin.addValueChangeListener(e -> {
			filter.setStartId(toLong(e.getValue()));
			updateState();
		});
		idMax.addValueChangeListener(e -> {
			filter.setEndId(toLong(e.getValue()));
			updateState();
		});
		createdStart.addValueChangeListener(e -> {
			filter.setCreatedAtStart(toInstant(e.getValue()));
			updateState();
		});
		createdEnd.addValueChangeListener(e -> {
			filter.setCreatedAtEnd(toInstant(e.getValue()));
			updateState();
		});
		updatedStart.addValueChangeListener(e -> {
			filter.setUpdatedAtStart(toInstant(e.getValue()));
			updateState();
		});
		updatedEnd.addValueChangeListener(e -> {
			filter.setUpdatedAtEnd(toInstant(e.getValue()));
			updateState();
		});

		applyButton.addClickListener(e -> {
			highlightChangedFields(false);
			onApply.run();
		});

		clearButton.addClickListener(e -> {
			clearAllFields();
			filter.clear();
			highlightChangedFields(false);
			onApply.run();
		});
	}

	@Override
	protected AdvertisementFilter cloneFilter(AdvertisementFilter original) {
		AdvertisementFilter copy = new AdvertisementFilter();
		copy.copyFrom(original);
		return copy;
	}

	@Override
	protected void clearAllFields() {
		clearAll(titleField, categoryField, locationField, statusField,
			idMin, idMax, createdStart, createdEnd, updatedStart, updatedEnd);
	}

	@Override
	protected void highlightChangedFields(boolean enable) {
		if (!enable) {
			FilterHighlighterUtil.clearHighlight(titleField, categoryField, locationField, statusField,
				idMin, idMax, createdStart, createdEnd, updatedStart, updatedEnd);
			return;
		}
		FilterHighlighterUtil.highlight(titleField, filter.getTitleFilter(), defaultFilter.getTitleFilter());
		FilterHighlighterUtil.highlight(categoryField, filter.getCategoryFilter(), defaultFilter.getCategoryFilter());
		FilterHighlighterUtil.highlight(locationField, filter.getLocationFilter(), defaultFilter.getLocationFilter());
		FilterHighlighterUtil.highlight(statusField, filter.getStatusFilter(), defaultFilter.getStatusFilter());
		FilterHighlighterUtil.highlight(idMin, filter.getStartId(), defaultFilter.getStartId());
		FilterHighlighterUtil.highlight(idMax, filter.getEndId(), defaultFilter.getEndId());
		FilterHighlighterUtil.highlight(createdStart, filter.getCreatedAtStart(), defaultFilter.getCreatedAtStart());
		FilterHighlighterUtil.highlight(createdEnd, filter.getCreatedAtEnd(), defaultFilter.getCreatedAtEnd());
		FilterHighlighterUtil.highlight(updatedStart, filter.getUpdatedAtStart(), defaultFilter.getUpdatedAtStart());
		FilterHighlighterUtil.highlight(updatedEnd, filter.getUpdatedAtEnd(), defaultFilter.getUpdatedAtEnd());
	}

	@Override
	protected boolean isFilterActive() {
		return (filter.getTitleFilter() != null && !filter.getTitleFilter().isBlank())
			|| (filter.getCategoryFilter() != null && !filter.getCategoryFilter().isBlank())
			|| (filter.getLocationFilter() != null && !filter.getLocationFilter().isBlank())
			|| filter.getStatusFilter() != null
			|| filter.getStartId() != null || filter.getEndId() != null
			|| filter.getCreatedAtStart() != null || filter.getCreatedAtEnd() != null
			|| filter.getUpdatedAtStart() != null || filter.getUpdatedAtEnd() != null;
	}

	@Override
	protected boolean validate() {
		return isValidNumberRange(filter.getStartId(), filter.getEndId())
			&& isValidDateRange(filter.getCreatedAtStart(), filter.getCreatedAtEnd())
			&& isValidDateRange(filter.getUpdatedAtStart(), filter.getUpdatedAtEnd());
	}

	@Override
	protected void copyFilter(AdvertisementFilter source, AdvertisementFilter target) {
		target.copyFrom(source);
	}

	@Override
	protected void clearFilter(AdvertisementFilter target) {
		target.clear();
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
