package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_SIDEBAR_BUTTON_ADD;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_SIDEBAR_SECTION_FILTERS;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_SIDEBAR_SECTION_SORTING;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.ost.advertisement.services.I18nService;

@SpringComponent
@UIScope
public class AdvertisementLeftSidebar extends VerticalLayout {

	private final Button addAdvertisementButton;
	private final Details filtersBlock;
	private final Details sortingBlock;

	@Getter
	private final transient AdvertisementFilterFields filterFields;
	@Getter
	private final transient AdvertisementSortFields sortFields;

	public AdvertisementLeftSidebar(AdvertisementFilterFields filterFields, AdvertisementSortFields sortFields,
									I18nService i18n) {
		this.filterFields = filterFields;
		this.sortFields = sortFields;

		this.addAdvertisementButton = new Button(i18n.get(ADVERTISEMENT_SIDEBAR_BUTTON_ADD));
		this.filtersBlock = createDetails(i18n.get(ADVERTISEMENT_SIDEBAR_SECTION_FILTERS));
		this.sortingBlock = createDetails(i18n.get(ADVERTISEMENT_SIDEBAR_SECTION_SORTING));

		filtersBlock.add(filterFields.getFilterComponentList());
		sortingBlock.add(sortFields.getSortComponentList());
	}

	public void eventProcessor(Runnable onRefreshAction, Runnable onAddButton) {
		filterFields.eventProcessor(onRefreshAction);
		sortFields.eventProcessor(onRefreshAction);
		addAdvertisementButton.addClickListener(e -> onAddButton.run());
	}

	@PostConstruct
	private void init() {
		setPadding(true);
		setSpacing(true);
		setWidth("270px");
		setHeight("1000px");
		getStyle().set("box-shadow", "2px 0 4px rgba(0,0,0,0.05)");
		getStyle().set("transition", "height 0.3s ease");
		getStyle().set("background-color", "#f4f4f4");

		VerticalLayout collapsibleSidebar = new VerticalLayout();
		collapsibleSidebar.add(addAdvertisementButton, filtersBlock, sortingBlock);
		collapsibleSidebar.setSpacing(true);
		collapsibleSidebar.setPadding(false);

		add(collapsibleSidebar);
	}

	private Details createDetails(String label) {
		VerticalLayout component = new VerticalLayout();
		component.setSpacing(true);
		component.setPadding(false);

		Details detailsBlock = new Details(label, component);
		detailsBlock.setOpened(true);

		return detailsBlock;
	}
}

