package org.ost.advertisement.ui.views.components.query.sort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import lombok.Getter;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.sort.TriStateSortIcon.SortHighlightColor;
import org.ost.advertisement.ui.views.components.query.QueryActionBlockHandler;
import org.springframework.data.domain.Sort.Direction;

public class SortFieldsProcessor {

	protected final CustomSort defaultSort;
	@Getter
	protected final CustomSort originalSort;
	@Getter
	protected final CustomSort newSort;

	private final Map<String, TriStateSortIcon> fieldsMap = new LinkedHashMap<>();

	public SortFieldsProcessor(CustomSort defaultSort) {
		this.defaultSort = defaultSort;
		this.originalSort = defaultSort.copy();
		this.newSort = defaultSort.copy();
	}

	public void register(String property, TriStateSortIcon field, QueryActionBlockHandler queryActionBlockHandler) {
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
		for (Map.Entry<String, TriStateSortIcon> entry : fieldsMap.entrySet()) {
			String property = entry.getKey();
			TriStateSortIcon field = entry.getValue();
			field.setColor(refreshColor(property));
		}
	}

	private SortHighlightColor refreshColor(String property) {
		Direction newVal = newSort.getDirection(property);
		Direction origVal = originalSort.getDirection(property);
		Direction defVal = defaultSort.getDirection(property);

		if (Objects.equals(newVal, origVal)) {
			return Objects.equals(origVal, defVal)
				? SortHighlightColor.DEFAULT
				: SortHighlightColor.CUSTOM;
		}
		return SortHighlightColor.CHANGED;
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

		for (Map.Entry<String, TriStateSortIcon> entry : fieldsMap.entrySet()) {
			String property = entry.getKey();
			TriStateSortIcon field = entry.getValue();
			field.setDirection(newSort.getDirection(property));
			field.setColor(refreshColor(property));
		}
	}

	public List<String> getSortDescriptions(I18nService i18n, UnaryOperator<String> labelProvider) {
		return newSort.getSort().stream()
			.map(order -> {
				String label = labelProvider.apply(order.getProperty());
				String direction = switch (order.getDirection()) {
					case ASC -> i18n.get(I18nKey.SORT_DIRECTION_ASC);
					case DESC -> i18n.get(I18nKey.SORT_DIRECTION_DESC);
				};
				return label + " (" + direction + ")";
			})
			.toList();
	}
}

