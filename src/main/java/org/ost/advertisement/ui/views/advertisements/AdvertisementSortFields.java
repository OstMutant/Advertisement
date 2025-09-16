package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.List;
import lombok.Getter;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.ui.views.components.sort.SortActionsBlock;
import org.ost.advertisement.ui.views.components.sort.SortFieldsProcessor;
import org.springframework.data.domain.Sort.Direction;

@SpringComponent
@UIScope
public class AdvertisementSortFields {

	private final ComboBox<Direction> titleCombo = createCombo();
	private final ComboBox<Direction> createdAtCombo = createCombo();
	private final ComboBox<Direction> updatedAtCombo = createCombo();

	private final SortActionsBlock actionsBlock = new SortActionsBlock();

	@Getter
	private final SortFieldsProcessor sortFieldsProcessor;

	@Getter
	private final List<Component> sortComponentList = List.of(
		createSortableField("Title", titleCombo),
		createSortableField("Created At", createdAtCombo),
		createSortableField("Updated At", updatedAtCombo),
		actionsBlock.getActionBlock());

	protected AdvertisementSortFields() {
		sortFieldsProcessor = new SortFieldsProcessor(new CustomSort());
		sortFieldsProcessor.register(titleCombo, "title", actionsBlock);
		sortFieldsProcessor.register(createdAtCombo, "createdAt", actionsBlock);
		sortFieldsProcessor.register(updatedAtCombo, "updatedAt", actionsBlock);

	}

	public void eventProcessor(Runnable onApply) {
		Runnable combinedOnApply = () -> {
			onApply.run();
			sortFieldsProcessor.refreshSorting();
			actionsBlock.onEventSortChanged(sortFieldsProcessor.isSortingChanged());
		};
		actionsBlock.eventProcessor(() -> {
			sortFieldsProcessor.updateSorting();
			combinedOnApply.run();
		}, () -> {
			sortFieldsProcessor.clearSorting();
			combinedOnApply.run();
		});
	}

	private HorizontalLayout createSortableField(String label, ComboBox<Direction> combo) {
		HorizontalLayout layout = new HorizontalLayout(new Span(label), combo);
		layout.setAlignItems(Alignment.CENTER);
		return layout;
	}

	private ComboBox<Direction> createCombo() {
		ComboBox<Direction> comboBox = new ComboBox<>();
		comboBox.setItems(Direction.ASC, Direction.DESC);
		comboBox.setClearButtonVisible(true);
		comboBox.setWidth("110px");
		return comboBox;
	}
}
