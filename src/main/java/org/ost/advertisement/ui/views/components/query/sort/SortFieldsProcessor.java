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
import org.ost.advertisement.ui.views.components.SvgIcon;
import org.ost.advertisement.ui.views.components.query.QueryActionBlockChangeListener;
import org.ost.advertisement.ui.views.components.SvgIcon.SortHighlightColor;
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

	public void register(TriStateSortIcon field, String property, QueryActionBlockChangeListener events) {
		fieldsMap.put(field, property);
		field.setDirection(newSort.getDirection(property));
		field.addDirectionChangedListener(e -> {
			newSort.updateSort(property, e.getDirection());
			events.setChanged(isSortingChanged());
			refreshSorting();
		});
	}

	public void refreshSorting() {
		for (Map.Entry<TriStateSortIcon, String> entry : fieldsMap.entrySet()) {
			TriStateSortIcon field = entry.getKey();
			String property = entry.getValue();

			Direction newVal = newSort.getDirection(property);
			Direction origVal = originalSort.getDirection(property);
			Direction defVal = defaultSort.getDirection(property);

			SortHighlightColor sortHighlightColor = Objects.equals(newVal, origVal)
				? (Objects.equals(origVal, defVal) ? SvgIcon.SortHighlightColor.DEFAULT : SvgIcon.SortHighlightColor.CUSTOM)
				: SvgIcon.SortHighlightColor.CHANGED;

			field.setVisualColor(sortHighlightColor);
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

		for (Map.Entry<TriStateSortIcon, String> entry : fieldsMap.entrySet()) {
			TriStateSortIcon field = entry.getKey();
			String property = entry.getValue();
			field.setDirection(newSort.getDirection(property));
		}
		refreshSorting();
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

