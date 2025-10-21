package org.ost.advertisement.ui.views.components.filters;

import lombok.Getter;
import org.ost.advertisement.mappers.filters.FilterMapper;
import org.ost.advertisement.services.ValidationService;

public abstract class AbstractFilterFields<F> {

	@Getter
	protected final FilterFieldsProcessor<F> filterFieldsProcessor;

	protected AbstractFilterFields(F defaultFilter, ValidationService<F> validation, FilterMapper<F> filterMapper) {
		this.filterFieldsProcessor = new FilterFieldsProcessor<>(filterMapper, validation, defaultFilter);
	}

	protected boolean isValidProperty(F filter, String property) {
		return filterFieldsProcessor.getValidation().isValidProperty(filter, property);
	}

	public abstract void eventProcessor(Runnable onApply);
}

