package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_SORT_CREATED_AT;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_SORT_TITLE;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_SORT_UPDATED_AT;
import static org.ost.advertisement.constans.I18nKey.SORT_DIRECTION_ASC;
import static org.ost.advertisement.constans.I18nKey.SORT_DIRECTION_DESC;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.Getter;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.sort.SortActionsBlock;
import org.ost.advertisement.ui.views.components.sort.SortFieldsProcessor;
import org.springframework.data.domain.Sort.Direction;

@SpringComponent
@UIScope
public class AdvertisementSortFields {

	private final ComboBox<Direction> titleCombo;
	private final ComboBox<Direction> createdAtCombo;
	private final ComboBox<Direction> updatedAtCombo;

	private final SortActionsBlock actionsBlock;

	@Getter
	private final SortFieldsProcessor sortFieldsProcessor;

	@Getter
	private final List<Component> sortComponentList;

	public AdvertisementSortFields(I18nService i18n) {
		this.sortFieldsProcessor = new SortFieldsProcessor(new CustomSort());

		this.titleCombo = createDirectionCombo(i18n);
		this.createdAtCombo = createDirectionCombo(i18n);
		this.updatedAtCombo = createDirectionCombo(i18n);
		this.actionsBlock = new SortActionsBlock(i18n);

		this.sortComponentList = List.of(
			createSortableField(i18n.get(ADVERTISEMENT_SORT_TITLE), titleCombo),
			createSortableField(i18n.get(ADVERTISEMENT_SORT_CREATED_AT), createdAtCombo),
			createSortableField(i18n.get(ADVERTISEMENT_SORT_UPDATED_AT), updatedAtCombo),
			actionsBlock.getActionBlock()
		);
	}

	@PostConstruct
	private void init() {
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

	private ComboBox<Direction> createDirectionCombo(I18nService i18n) {
		ComboBox<Direction> comboBox = new ComboBox<>();
		comboBox.setItems(Direction.ASC, Direction.DESC);
		comboBox.setItemLabelGenerator(dir -> switch (dir) {
			case ASC -> i18n.get(SORT_DIRECTION_ASC);
			case DESC -> i18n.get(SORT_DIRECTION_DESC);
		});
		comboBox.setClearButtonVisible(true);
		comboBox.setWidth("110px");
		return comboBox;
	}
}

