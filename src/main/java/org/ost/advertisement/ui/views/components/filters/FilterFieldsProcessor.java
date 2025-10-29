package org.ost.advertisement.ui.views.components.filters;

import static org.ost.advertisement.ui.utils.FilterHighlighterUtil.highlight;
import static org.ost.advertisement.ui.utils.SupportUtil.hasChanged;

import com.vaadin.flow.component.AbstractField;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import lombok.Getter;
import org.ost.advertisement.mappers.filters.FilterMapper;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.ActionStateChangeListener;

public class FilterFieldsProcessor<F> {

	private final FilterMapper<F> filterMapper;

	@Getter
	private final ValidationService<F> validationService;

	private final F defaultFilter;
	@Getter
	private final F originalFilter;
	@Getter
	private final F newFilter;

	public FilterFieldsProcessor(FilterMapper<F> filterMapper, ValidationService<F> validationService, F defaultFilter) {
		this.filterMapper = filterMapper;
		this.validationService = validationService;
		this.defaultFilter = defaultFilter;
		this.originalFilter = filterMapper.copy(defaultFilter);
		this.newFilter = filterMapper.copy(defaultFilter);
	}

	private record FilterFieldsRelationship<F, R>(
		AbstractField<?, ?> field,
		Function<F, R> getter,
		BiPredicate<ValidationService<F>, F> validation
	) {

	}

	private final Set<FilterFieldsRelationship<F, ?>> fieldsRelationships = new HashSet<>();

	public <T, C extends AbstractField<?, T>, R> void register(C field,
															   BiConsumer<F, T> setter,
															   Function<F, R> getter,
															   BiPredicate<ValidationService<F>, F> validation,
															   ActionStateChangeListener events) {
		fieldsRelationships.add(new FilterFieldsRelationship<>(field, getter, validation));
		field.addValueChangeListener(e -> {
			setter.accept(newFilter, e.getValue());
			events.setChanged(isFilterChanged());
			refreshFilter();
		});
	}

	public void refreshFilter() {
		highlightChangedFields();
	}

	public void updateFilter() {
		filterMapper.update(originalFilter, newFilter);
	}

	public void clearFilter() {
		for (FilterFieldsRelationship<F, ?> fieldRelationship : fieldsRelationships) {
			fieldRelationship.field.clear();
		}
		filterMapper.update(newFilter, defaultFilter);
		filterMapper.update(originalFilter, defaultFilter);
	}

	public boolean isFilterChanged() {
		return validate() && hasChanged(newFilter, originalFilter);
	}

	public boolean validate() {
		return validationService.isValid(this.newFilter);
	}

	private void highlightChangedFields() {
		for (FilterFieldsRelationship<F, ?> fieldRelationship : fieldsRelationships) {
			highlight(fieldRelationship.field, fieldRelationship.getter.apply(newFilter),
				fieldRelationship.getter.apply(originalFilter), fieldRelationship.getter.apply(defaultFilter),
				fieldRelationship.validation.test(validationService, newFilter));
		}
	}
}
