package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.toInstant;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.List;
import java.util.function.Predicate;
import lombok.Getter;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.ost.advertisement.mappers.AdvertisementFilterMapper;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.filters.AbstractFilterFields;
import org.ost.advertisement.ui.views.components.filters.FilterActionsBlock;

@SpringComponent
@UIScope
public class AdvertisementFilterFields extends AbstractFilterFields<AdvertisementFilter> {

	private final TextField title = createFullTextField("Title...");

	private final DatePicker createdStart = createDatePicker("Created from");
	private final DatePicker createdEnd = createDatePicker("Created to");
	private final DatePicker updatedStart = createDatePicker("Updated from");
	private final DatePicker updatedEnd = createDatePicker("Updated to");

	private final FilterActionsBlock actionsBlock = new FilterActionsBlock();

	@Getter
	private final List<Component> filterComponentList = List.of(
		title,
		createFilterBlock(createdStart, createdEnd),
		createFilterBlock(updatedStart, updatedEnd),
		actionsBlock.getActionBlock()
	);

	public AdvertisementFilterFields(AdvertisementFilterMapper filterMapper, ValidationService<AdvertisementFilter> validation) {
		super(AdvertisementFilter.empty(), AdvertisementFilter.empty(), AdvertisementFilter.empty(), validation, filterMapper);

		register(title, (f, v) -> f.setTitle(v == null || v.isBlank() ? null : v), AdvertisementFilter::getTitle,
			f -> !validation.hasViolationFor(f, "title"));

		Predicate<AdvertisementFilter> validationCreatedAt =
			f -> !validation.hasViolationFor(f, "createdAtStart") && !validation.hasViolationFor(f, "createdAtEnd");
		register(createdStart, (f, v) -> f.setCreatedAtStart(toInstant(v)), AdvertisementFilter::getCreatedAtStart,
			validationCreatedAt);
		register(createdEnd, (f, v) -> f.setCreatedAtEnd(toInstant(v)), AdvertisementFilter::getCreatedAtEnd,
			validationCreatedAt);

		Predicate<AdvertisementFilter> validationUpdatedAt =
			f -> !validation.hasViolationFor(f, "updatedAtStart") && !validation.hasViolationFor(f, "updatedAtEnd");
		register(updatedStart, (f, v) -> f.setUpdatedAtStart(toInstant(v)), AdvertisementFilter::getUpdatedAtStart,
			validationUpdatedAt);
		register(updatedEnd, (f, v) -> f.setUpdatedAtEnd(toInstant(v)), AdvertisementFilter::getUpdatedAtEnd,
			validationUpdatedAt);
	}

	@Override
	protected void refreshFilter() {
		super.refreshFilter();
		actionsBlock.updateButtonState(isFilterActive());
	}


	@Override
	public void eventProcessor(Runnable onApply) {
		actionsBlock.eventProcessor(() -> {
			if (!validate()) {
				return;
			}
			updateFilter();
			onApply.run();
			refreshFilter();
		}, () -> {
			clearFilter();
			onApply.run();
			refreshFilter();
		});
	}
}
