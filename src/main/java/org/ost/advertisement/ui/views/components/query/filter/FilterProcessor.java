package org.ost.advertisement.ui.views.components.query.filter;

import com.vaadin.flow.component.AbstractField;
import lombok.Getter;
import org.ost.advertisement.mappers.filters.FilterMapper;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.query.action.QueryActionBlockHandler;
import org.ost.advertisement.ui.views.components.query.filter.meta.FilterFieldMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.ost.advertisement.ui.utils.FilterHighlighterUtil.highlight;
import static org.ost.advertisement.ui.utils.SupportUtil.hasChanged;

public class FilterProcessor<F> {

    private final FilterMapper<F> filterMapper;

    @Getter
    private final ValidationService<F> validationService;

    private final F defaultFilter;
    @Getter
    private final F originalFilter;
    @Getter
    private final F newFilter;

    private final Map<FilterFieldMeta<?, F, ?>, AbstractField<?, ?>> fieldsMap = new LinkedHashMap<>();

    public FilterProcessor(FilterMapper<F> filterMapper, ValidationService<F> validationService,
                           F defaultFilter) {
        this.filterMapper = filterMapper;
        this.validationService = validationService;
        this.defaultFilter = defaultFilter;
        this.originalFilter = filterMapper.copy(defaultFilter);
        this.newFilter = filterMapper.copy(defaultFilter);
    }

    public <I, C extends AbstractField<?, I>, R> void register(FilterFieldMeta<I, F, R> meta, C field,
                                                               QueryActionBlockHandler queryActionBlockHandler) {
        fieldsMap.put(meta, field);
        field.addValueChangeListener(e -> {
            meta.setter().accept(newFilter, e.getValue());
            queryActionBlockHandler.updateDirtyState(isFilterChanged());
            refreshItemsFilter();
        });
    }

    public void refreshItemsFilter() {
        highlightChangedFields();
    }

    public void updateFilter() {
        filterMapper.update(originalFilter, newFilter);
    }

    public void clearFilter() {
        fieldsMap.values().forEach(AbstractField::clear);
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
        return fieldsMap.keySet().stream()
                .filter(filterFieldMeta -> filterFieldMeta.hasValue(newFilter))
                .map(filterFieldMeta -> filterFieldMeta.name() + ": " + filterFieldMeta.getValueAsString(newFilter))
                .toList();
    }

    private void highlightChangedFields() {
        for (Map.Entry<FilterFieldMeta<?, F, ?>, AbstractField<?, ?>> entry : fieldsMap.entrySet()) {
            AbstractField<?, ?> field = entry.getValue();
            FilterFieldMeta<?, F, ?> filterFieldMeta = entry.getKey();
            highlight(
                    field,
                    filterFieldMeta.getter().apply(newFilter),
                    filterFieldMeta.getter().apply(originalFilter),
                    filterFieldMeta.getter().apply(defaultFilter),
                    filterFieldMeta.validation().test(validationService, newFilter)
            );
        }
    }
}
