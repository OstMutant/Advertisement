package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.ui.views.components.sort.AbstractSortFields;
import org.ost.advertisement.ui.views.components.sort.SortActionsBlock;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

public class AdvertisementSortFields extends AbstractSortFields {

	private final ComboBox<Direction> titleCombo = createCombo();
	private final ComboBox<Direction> createdAtCombo = createCombo();
	private final ComboBox<Direction> updatedAtCombo = createCombo();

	private final SortActionsBlock actionsBlock = new SortActionsBlock();

	protected AdvertisementSortFields() {
		super(new CustomSort(Sort.unsorted()));
	}

	@Override
	protected void updateState() {
		super.updateState();
		actionsBlock.updateButtonState(isSortActive());
	}

	@Override
	public void configure(Runnable onApply) {
		actionsBlock.configure(() -> {
			originalSort.copyFrom(this.newSort);
			onApply.run();
			updateState();
		}, () -> {
			clearAllFields();
			newSort.clear();
			originalSort.clear();
			onApply.run();
			updateState();
		});
		register(titleCombo, "title");
		register(createdAtCombo, "createdAt");
		register(updatedAtCombo, "updatedAt");
	}

	private HorizontalLayout createSortableField(String label, ComboBox<Direction> combo) {
		HorizontalLayout layout = new HorizontalLayout(new Span(label), combo);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}

	protected ComboBox<Direction> createCombo() {
		ComboBox<Direction> comboBox = new ComboBox<>();
		comboBox.setItems(Direction.ASC, Direction.DESC);
		comboBox.setClearButtonVisible(true);
		comboBox.setWidth("110px");
		return comboBox;
	}

	public HorizontalLayout getTitleBlock() {
		return createSortableField("Title", titleCombo);
	}

	public HorizontalLayout getCreatedAtBlock() {
		return createSortableField("Created At", createdAtCombo);
	}

	public HorizontalLayout getUpdatedAtBlock() {
		return createSortableField("Updated At", updatedAtCombo);
	}

	public Component getActionBlock() {
		return actionsBlock.getActionBlock();
	}

}
