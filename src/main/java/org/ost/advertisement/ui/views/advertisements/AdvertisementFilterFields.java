package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.toInstant;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
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

	public AdvertisementFilterFields(AdvertisementFilterMapper filterMapper,
									 ValidationService<AdvertisementFilter> validation) {
		super(AdvertisementFilter.empty(), AdvertisementFilter.empty(), AdvertisementFilter.empty(), validation,
			filterMapper);
	}

	@PostConstruct
	private void init() {
		filterFieldsProcessor.register(title, (f, v) -> f.setTitle(v == null || v.isBlank() ? null : v),
			AdvertisementFilter::getTitle, f -> isValidProperty(f, "title"), actionsBlock);

		Predicate<AdvertisementFilter> validationCreatedAt =
			f -> isValidProperty(f, "createdAtStart") && isValidProperty(f, "createdAtEnd");
		filterFieldsProcessor.register(createdStart, (f, v) -> f.setCreatedAtStart(toInstant(v)),
			AdvertisementFilter::getCreatedAtStart, validationCreatedAt, actionsBlock);
		filterFieldsProcessor.register(createdEnd, (f, v) -> f.setCreatedAtEnd(toInstant(v)),
			AdvertisementFilter::getCreatedAtEnd, validationCreatedAt, actionsBlock);

		Predicate<AdvertisementFilter> validationUpdatedAt =
			f -> isValidProperty(f, "updatedAtStart") && isValidProperty(f, "updatedAtEnd");
		filterFieldsProcessor.register(updatedStart, (f, v) -> f.setUpdatedAtStart(toInstant(v)),
			AdvertisementFilter::getUpdatedAtStart, validationUpdatedAt, actionsBlock);
		filterFieldsProcessor.register(updatedEnd, (f, v) -> f.setUpdatedAtEnd(toInstant(v)),
			AdvertisementFilter::getUpdatedAtEnd, validationUpdatedAt, actionsBlock);
	}

	@Override
	public void eventProcessor(Runnable onApply) {
		Runnable combinedOnApply = () -> {
			onApply.run();
			filterFieldsProcessor.refreshFilter();
			actionsBlock.onEventFilterChanged(filterFieldsProcessor.isFilterChanged());
		};

		actionsBlock.eventProcessor(() -> {
			if (!filterFieldsProcessor.validate()) {
				return;
			}
			filterFieldsProcessor.updateFilter();
			combinedOnApply.run();
		}, () -> {
			filterFieldsProcessor.clearFilter();
			combinedOnApply.run();
		});
	}
}
