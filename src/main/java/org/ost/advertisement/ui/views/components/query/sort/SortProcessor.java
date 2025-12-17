package org.ost.advertisement.ui.views.components.query.sort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.Getter;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.ui.views.components.query.action.QueryActionBlockHandler;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon.SortHighlightColor;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;

public class SortProcessor {

	protected final CustomSort defaultSort;
	@Getter
	protected final CustomSort originalSort;
	@Getter
	protected final CustomSort newSort;

	private final Map<String, SortIcon> fieldsMap = new LinkedHashMap<>();

	public SortProcessor(CustomSort defaultSort) {
		this.defaultSort = defaultSort;
		this.originalSort = defaultSort.copy();
		this.newSort = defaultSort.copy();
	}

	public void register(String property, SortIcon field, QueryActionBlockHandler queryActionBlockHandler) {
		fieldsMap.put(property, field);
		field.setDirection(newSort.getDirection(property));
		field.addDirectionChangedListener(e -> {
			newSort.updateSort(property, e.getDirection());
			queryActionBlockHandler.updateDirtyState(isSortingChanged());
			field.setColor(refreshColor(property));
		});
		field.setColor(refreshColor(property));
	}

	public void refreshItemsColor() {
		for (Map.Entry<String, SortIcon> entry : fieldsMap.entrySet()) {
			refreshItemColor(entry.getKey(), entry.getValue());
		}
	}

	public boolean isSortingChanged() {
		return !originalSort.areSortsEquivalent(newSort);
	}

	public void updateSorting() {
		originalSort.copyFrom(newSort);
	}

	public void clearSorting() {
		originalSort.copyFrom(defaultSort);
		newSort.copyFrom(defaultSort);

		for (Map.Entry<String, SortIcon> entry : fieldsMap.entrySet()) {
			updateItemDirectionAndRefreshColor(entry.getKey(), entry.getValue());
		}
	}

	public List<String> getSortDescriptions(Function<Order, String> transformer) {
		return newSort.getSort().stream().map(transformer).toList();
	}

	private void updateItemDirectionAndRefreshColor(String property, SortIcon field) {
		field.setDirection(newSort.getDirection(property));
		refreshItemColor(property, field);
	}

	private void refreshItemColor(String property, SortIcon field) {
		field.setColor(refreshColor(property));
	}

	private SortHighlightColor refreshColor(String property) {
		Direction newVal = newSort.getDirection(property);
		Direction origVal = originalSort.getDirection(property);
		Direction defVal = defaultSort.getDirection(property);

		if (Objects.equals(newVal, origVal)) {
			return Objects.equals(origVal, defVal) ? SortHighlightColor.DEFAULT : SortHighlightColor.CUSTOM;
		}
		return SortHighlightColor.CHANGED;
	}
}

