package org.ost.advertisement.ui.views.components.sort;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.Getter;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.ui.views.components.ActionStateChangeListener;
import org.springframework.data.domain.Sort.Direction;

public class SortFieldsProcessor {

	protected CustomSort defaultSort;
	@Getter
	protected CustomSort originalSort;
	@Getter
	protected CustomSort newSort;

	public record SortFieldsRelationship(
		TriStateSortIcon field,
		Function<CustomSort, Direction> getter,
		Runnable clearAction
	) {

	}

	protected final Set<SortFieldsRelationship> fieldsRelationships = new HashSet<>();

	public SortFieldsProcessor(CustomSort defaultSort) {
		this.defaultSort = defaultSort;
		this.originalSort = defaultSort.copy();
		this.newSort = defaultSort.copy();
	}

	public void register(TriStateSortIcon field, String property, ActionStateChangeListener events) {
		fieldsRelationships.add(new SortFieldsRelationship(
			field,
			v -> v.getDirection(property),
			field::clear
		));
		field.addDirectionChangedListener(e -> {
			newSort.updateSort(property, e.getDirection());
			events.setChanged(isSortingChanged());
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
		for (SortFieldsRelationship relationship : fieldsRelationships) {
			relationship.field.clear();
		}
		originalSort.copyFrom(defaultSort);
		newSort.copyFrom(defaultSort);
	}

	protected void highlightChangedFields() {
		for (SortFieldsRelationship rel : fieldsRelationships) {
			Direction newVal = rel.getter.apply(newSort);
			Direction origVal = rel.getter.apply(originalSort);
			Direction defVal = rel.getter.apply(defaultSort);

			String color = Objects.equals(newVal, origVal)
				? (Objects.equals(origVal, defVal) ? "gray" : "green")
				: "orange";

			rel.field.setVisualColor(color);
		}
	}
}
