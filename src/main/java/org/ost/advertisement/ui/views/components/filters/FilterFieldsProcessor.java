package org.ost.advertisement.ui.views.components.filters;

import static org.ost.advertisement.ui.utils.FilterHighlighterUtil.highlight;
import static org.ost.advertisement.ui.utils.SupportUtil.hasChanged;

import com.vaadin.flow.component.AbstractField;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.Getter;
import org.ost.advertisement.mappers.FilterMapper;
import org.ost.advertisement.services.ValidationService;

public class FilterFieldsProcessor<F> {

	private final FilterMapper<F> filterMapper;

	@Getter
	private final ValidationService<F> validation;

	private final F defaultFilter;
	@Getter
	private final F originalFilter;
	@Getter
	private final F newFilter;

	public FilterFieldsProcessor(FilterMapper<F> filterMapper, ValidationService<F> validation, F defaultFilter) {
		this.filterMapper = filterMapper;
		this.validation = validation;
		this.defaultFilter = defaultFilter;
		this.originalFilter = filterMapper.copy(defaultFilter);
		this.newFilter = filterMapper.copy(defaultFilter);
	}

	private record FilterFieldsRelationship<F, T>(
		AbstractField<?, ?> field,
		Function<F, T> getter,
		Predicate<F> validation
	) {

	}

	protected final Set<FilterFieldsRelationship<F, ?>> fieldsRelationships = new HashSet<>();

	public <T, C extends AbstractField<?, T>, R> void register(C field, BiConsumer<F, T> setter,
															   Function<F, R> getter, Predicate<F> validation,
															   FilterFieldsProcessorEvents events) {
		fieldsRelationships.add(new FilterFieldsRelationship<>(field, getter, validation));
		field.addValueChangeListener(e -> {
			setter.accept(newFilter, e.getValue());
			events.onEventFilterChanged(isFilterChanged());
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
		for (FilterFieldsRelationship<?, ?> fieldRelationship : fieldsRelationships) {
			fieldRelationship.field.clear();
		}
		filterMapper.update(newFilter, defaultFilter);
		filterMapper.update(originalFilter, defaultFilter);
	}

	public boolean isFilterChanged() {
		return validate() && hasChanged(newFilter, originalFilter);
	}

	public boolean validate() {
		return validation.isValid(this.newFilter);
	}

	private void highlightChangedFields() {
		for (FilterFieldsRelationship<F, ?> fieldRelationship : fieldsRelationships) {
			highlight(fieldRelationship.field, fieldRelationship.getter.apply(newFilter),
				fieldRelationship.getter.apply(originalFilter), fieldRelationship.getter.apply(defaultFilter)
				, fieldRelationship.validation.test(newFilter));
		}
	}
}
