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

public abstract class AbstractSortFields {

	protected CustomSort defaultSort;
	@Getter
	protected CustomSort originalSort;
	@Getter
	protected CustomSort newSort;

	public record SortFieldsRelationship<F, T>(
		AbstractField<?, ?> field,
		Function<CustomSort, T> getter
	) {

	}

	protected final Set<SortFieldsRelationship<CustomSort, ?>> fieldsRelationships = new HashSet<>();

	protected AbstractSortFields(CustomSort defaultSort) {
		this.defaultSort = defaultSort;
		this.originalSort = defaultSort.copy();
		this.newSort = defaultSort.copy();
	}

	public abstract void configure(Runnable onApply);

	protected void register(ComboBox<Direction> field, String property) {
		fieldsRelationships.add(new SortFieldsRelationship<>(field, v-> v.getDirection(property)));
		field.addValueChangeListener(e -> {
			newSort.updateSort(property, e.getValue());
			updateState();
		});
	}

	protected void updateState() {
		highlightChangedFields();
	}

	protected boolean isSortActive() {
		return !originalSort.areSortsEquivalent(newSort);
	}

	protected void clearAllFields() {
		for (SortFieldsRelationship<?, ?> relationship : fieldsRelationships) {
			relationship.field.clear();
		}
	}

	protected void highlightChangedFields() {
		for (SortFieldsRelationship<CustomSort, ?> fieldRelationship : fieldsRelationships) {
			highlight(fieldRelationship.field, fieldRelationship.getter.apply(newSort),
				fieldRelationship.getter.apply(originalSort), fieldRelationship.getter.apply(defaultSort), true);
		}
	}
}
