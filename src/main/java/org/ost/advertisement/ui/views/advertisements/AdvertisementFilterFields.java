package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_FILTER_CREATED_END;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_FILTER_CREATED_START;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_FILTER_UPDATED_END;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_FILTER_UPDATED_START;
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
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.mappers.filters.AdvertisementFilterMapper;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.filters.AbstractFilterFields;
import org.ost.advertisement.ui.views.components.filters.FilterActionsBlock;

@SpringComponent
@UIScope
public class AdvertisementFilterFields extends AbstractFilterFields<AdvertisementFilterDto> {

	private final TextField title;
	private final DatePicker createdStart;
	private final DatePicker createdEnd;
	private final DatePicker updatedStart;
	private final DatePicker updatedEnd;

	private final FilterActionsBlock actionsBlock;

	@Getter
	private final List<Component> filterComponentList;

	public AdvertisementFilterFields(AdvertisementFilterMapper filterMapper,
									 ValidationService<AdvertisementFilterDto> validation,
									 I18nService i18n) {
		super(AdvertisementFilterDto.empty(), validation, filterMapper);

		this.title = createFullTextField(i18n.get(ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER));
		this.createdStart = createDatePicker(i18n.get(ADVERTISEMENT_FILTER_CREATED_START));
		this.createdEnd = createDatePicker(i18n.get(ADVERTISEMENT_FILTER_CREATED_END));
		this.updatedStart = createDatePicker(i18n.get(ADVERTISEMENT_FILTER_UPDATED_START));
		this.updatedEnd = createDatePicker(i18n.get(ADVERTISEMENT_FILTER_UPDATED_END));
		this.actionsBlock = new FilterActionsBlock(i18n);

		this.filterComponentList = List.of(
			title,
			createFilterBlock(createdStart, createdEnd),
			createFilterBlock(updatedStart, updatedEnd),
			actionsBlock.getActionBlock()
		);
	}

	@PostConstruct
	private void init() {
		filterFieldsProcessor.register(title, (f, v) -> f.setTitle(v == null || v.isBlank() ? null : v),
			AdvertisementFilterDto::getTitle, f -> isValidProperty(f, "title"), actionsBlock);

		Predicate<AdvertisementFilterDto> validationCreatedAt =
			f -> isValidProperty(f, "createdAtStart") && isValidProperty(f, "createdAtEnd");
		filterFieldsProcessor.register(createdStart, (f, v) -> f.setCreatedAtStart(toInstant(v)),
			AdvertisementFilterDto::getCreatedAtStart, validationCreatedAt, actionsBlock);
		filterFieldsProcessor.register(createdEnd, (f, v) -> f.setCreatedAtEnd(toInstant(v)),
			AdvertisementFilterDto::getCreatedAtEnd, validationCreatedAt, actionsBlock);

		Predicate<AdvertisementFilterDto> validationUpdatedAt =
			f -> isValidProperty(f, "updatedAtStart") && isValidProperty(f, "updatedAtEnd");
		filterFieldsProcessor.register(updatedStart, (f, v) -> f.setUpdatedAtStart(toInstant(v)),
			AdvertisementFilterDto::getUpdatedAtStart, validationUpdatedAt, actionsBlock);
		filterFieldsProcessor.register(updatedEnd, (f, v) -> f.setUpdatedAtEnd(toInstant(v)),
			AdvertisementFilterDto::getUpdatedAtEnd, validationUpdatedAt, actionsBlock);
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

