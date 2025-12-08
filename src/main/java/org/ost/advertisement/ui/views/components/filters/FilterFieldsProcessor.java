package org.ost.advertisement.ui.views.components.filters;

import static org.ost.advertisement.ui.utils.FilterHighlighterUtil.highlight;
import static org.ost.advertisement.ui.utils.SupportUtil.hasChanged;

import com.vaadin.flow.component.AbstractField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.ost.advertisement.mappers.filters.FilterMapper;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.ActionStateChangeListener;
import org.ost.advertisement.ui.views.components.filters.meta.FilterField;

public class FilterFieldsProcessor<F> {

	private final FilterMapper<F> filterMapper;

	@Getter
	private final ValidationService<F> validationService;

	private final F defaultFilter;
	@Getter
	private final F originalFilter;
	@Getter
	private final F newFilter;

	private final Map<AbstractField<?, ?>, FilterField<?, F, ?>> fieldsMap = new LinkedHashMap<>();

	public FilterFieldsProcessor(FilterMapper<F> filterMapper, ValidationService<F> validationService,
								 F defaultFilter) {
		this.filterMapper = filterMapper;
		this.validationService = validationService;
		this.defaultFilter = defaultFilter;
		this.originalFilter = filterMapper.copy(defaultFilter);
		this.newFilter = filterMapper.copy(defaultFilter);
	}

	public <I, C extends AbstractField<?, I>, R> void register(C field,
															   FilterField<I, F, R> meta,
															   ActionStateChangeListener events) {
		fieldsMap.put(field, meta);
		field.addValueChangeListener(e -> {
			meta.setter().accept(newFilter, e.getValue());
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
		fieldsMap.keySet().forEach(AbstractField::clear);
		filterMapper.update(newFilter, defaultFilter);
		filterMapper.update(originalFilter, defaultFilter);
	}

	public boolean isFilterChanged() {
		return validate() && hasChanged(newFilter, originalFilter);
	}

	public boolean validate() {
		return validationService.isValid(newFilter);
	}

	public List<String> getActiveFilterDescriptions() {
		return fieldsMap.values().stream()
			.filter(fFilterField -> fFilterField.hasValue(newFilter))
			.map(fFilterField -> fFilterField.name() + ": " + fFilterField.getValueAsString(newFilter))
			.toList();
	}

	private void highlightChangedFields() {
		for (Map.Entry<AbstractField<?, ?>, FilterField<?, F, ?>> entry : fieldsMap.entrySet()) {
			highlight(
				entry.getKey(),
				entry.getValue().getter().apply(newFilter),
				entry.getValue().getter().apply(originalFilter),
				entry.getValue().getter().apply(defaultFilter),
				entry.getValue().validation().test(validationService, newFilter)
			);
		}
	}
}
