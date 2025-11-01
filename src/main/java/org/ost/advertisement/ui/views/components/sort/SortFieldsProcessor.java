package org.ost.advertisement.ui.views.components.sort;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.ui.views.components.ActionStateChangeListener;
import org.springframework.data.domain.Sort.Direction;


public class SortFieldsProcessor {

	protected final CustomSort defaultSort;
	@Getter
	protected final CustomSort originalSort;
	@Getter
	protected final CustomSort newSort;

	private final Map<TriStateSortIcon, String> fieldsMap = new LinkedHashMap<>();

	public SortFieldsProcessor(CustomSort defaultSort) {
		this.defaultSort = defaultSort;
		this.originalSort = defaultSort.copy();
		this.newSort = defaultSort.copy();
	}

	public void register(TriStateSortIcon field, String property, ActionStateChangeListener events) {
		fieldsMap.put(field, property);
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
		fieldsMap.keySet().forEach(TriStateSortIcon::clear);
		originalSort.copyFrom(defaultSort);
		newSort.copyFrom(defaultSort);
	}

	protected void highlightChangedFields() {
		for (Map.Entry<TriStateSortIcon, String> entry : fieldsMap.entrySet()) {
			TriStateSortIcon field = entry.getKey();
			String property = entry.getValue();

			Direction newVal = newSort.getDirection(property);
			Direction origVal = originalSort.getDirection(property);
			Direction defVal = defaultSort.getDirection(property);

			String color = Objects.equals(newVal, origVal)
				? (Objects.equals(origVal, defVal) ? "gray" : "green")
				: "orange";

			field.setVisualColor(color);
		}
	}
}

