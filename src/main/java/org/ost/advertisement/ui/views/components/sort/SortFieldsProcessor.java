package org.ost.advertisement.ui.views.components.sort;

import static org.ost.advertisement.ui.utils.FilterHighlighterUtil.highlight;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.combobox.ComboBox;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import lombok.Getter;
import org.ost.advertisement.dto.sort.CustomSort;
import org.springframework.data.domain.Sort.Direction;

public class SortFieldsProcessor {

	protected CustomSort defaultSort;
	@Getter
	protected CustomSort originalSort;
	@Getter
	protected CustomSort newSort;

	public record SortFieldsRelationship<T>(
		AbstractField<?, ?> field,
		Function<CustomSort, T> getter
	) {

	}

	protected final Set<SortFieldsRelationship<?>> fieldsRelationships = new HashSet<>();

	public SortFieldsProcessor(CustomSort defaultSort) {
		this.defaultSort = defaultSort;
		this.originalSort = defaultSort.copy();
		this.newSort = defaultSort.copy();
	}

	public void register(ComboBox<Direction> field, String property, SortFieldsProcessorEvents events) {
		fieldsRelationships.add(new SortFieldsRelationship<>(field, v -> v.getDirection(property)));
		field.addValueChangeListener(e -> {
			newSort.updateSort(property, e.getValue());
			events.onEventSortChanged(isSortingChanged());
			refreshSorting();
		});
	}

	public void refreshSorting() {
		highlightChangedFields();
	}

	public boolean isSortingChanged() {
		return !originalSort.areSortsEquivalent(newSort);
	}

	public void updateSorting() {
		originalSort.copyFrom(newSort);
	}

	public void clearSorting() {
		for (SortFieldsRelationship<?> relationship : fieldsRelationships) {
			relationship.field.clear();
		}
		originalSort.copyFrom(defaultSort);
		newSort.copyFrom(defaultSort);
	}

	protected void highlightChangedFields() {
		for (SortFieldsRelationship<?> fieldRelationship : fieldsRelationships) {
			highlight(fieldRelationship.field, fieldRelationship.getter.apply(newSort),
				fieldRelationship.getter.apply(originalSort), fieldRelationship.getter.apply(defaultSort), true);
		}
	}
}
