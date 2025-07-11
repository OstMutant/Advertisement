package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.FilterFieldsUtil.*;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.provider.ConfigurableFilterDataProvider;
import lombok.Getter;
import org.ost.advertisement.dto.AdvertisementFilter;
import org.ost.advertisement.entyties.Advertisement;
import org.ost.advertisement.ui.utils.FilterHighlighterUtil;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;

import java.util.List;

public class AdvertisementFilterFields {

	private final AdvertisementFilter defaultFilter = new AdvertisementFilter();
	@Getter
	private final AdvertisementFilter filter = new AdvertisementFilter();

	private final TextField titleField = createFullTextField("Title...");
	private final TextField categoryField = createShortTextField("Category...");
	private final TextField locationField = createShortTextField("Location...");

	private final Select<String> statusField = createSelect("Status", List.of("ACTIVE", "EXPIRED", "DRAFT"));

	private final NumberField idMin = createNumberField("Min ID");
	private final NumberField idMax = createNumberField("Max ID");

	private final DatePicker createdStart = createDatePicker("Created from");
	private final DatePicker createdEnd = createDatePicker("Created to");
	private final DatePicker updatedStart = createDatePicker("Updated from");
	private final DatePicker updatedEnd = createDatePicker("Updated to");

	private final Button applyButton = createButton(VaadinIcon.FILTER, "Apply filters", ButtonVariant.LUMO_PRIMARY);
	private final Button clearButton = createButton(VaadinIcon.ERASER, "Clear filters", ButtonVariant.LUMO_TERTIARY);

	public void configure(ConfigurableFilterDataProvider<Advertisement, Void, AdvertisementFilter> dataProvider) {
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
			if (!isValidNumberRange(filter.getStartId(), filter.getEndId()) ||
				!isValidDateRange(filter.getCreatedAtStart(), filter.getCreatedAtEnd()) ||
				!isValidDateRange(filter.getUpdatedAtStart(), filter.getUpdatedAtEnd())) {

				applyButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
				return;
			}

			applyButton.removeThemeVariants(ButtonVariant.LUMO_ERROR);
			dataProvider.setFilter(filter);
			defaultFilter.copyFrom(filter);
			highlightChanges(true);
		});

		clearButton.addClickListener(e -> {
			clearAll(titleField, categoryField, locationField, statusField,
				idMin, idMax, createdStart, createdEnd, updatedStart, updatedEnd);

			filter.clear();
			defaultFilter.clear();
			dataProvider.setFilter(filter);
			updateButtonState();
			highlightChanges(false);
		});
	}

	private void updateState() {
		updateButtonState();
		highlightChanges(true);
	}

	private void updateButtonState() {
		applyButton.removeThemeVariants(ButtonVariant.LUMO_CONTRAST);
		if (isActive()) {
			applyButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
		}
	}

	private boolean isActive() {
		return filter.getTitleFilter() != null && !filter.getTitleFilter().isBlank()
			|| filter.getCategoryFilter() != null && !filter.getCategoryFilter().isBlank()
			|| filter.getLocationFilter() != null && !filter.getLocationFilter().isBlank()
			|| filter.getStatusFilter() != null
			|| filter.getStartId() != null
			|| filter.getEndId() != null
			|| filter.getCreatedAtStart() != null
			|| filter.getCreatedAtEnd() != null
			|| filter.getUpdatedAtStart() != null
			|| filter.getUpdatedAtEnd() != null;
	}

	private void highlightChanges(boolean enable) {
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

	public Component getActionBlock() {
		HorizontalLayout actions = new HorizontalLayout(applyButton, clearButton);
		actions.setSpacing(false);
		actions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
		return actions;
	}
}
