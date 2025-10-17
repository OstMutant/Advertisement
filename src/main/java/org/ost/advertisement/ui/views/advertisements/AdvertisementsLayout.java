package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.PaginationBarModern;

public class AdvertisementsLayout extends HorizontalLayout {

	private final VerticalLayout advertisementContainer = new VerticalLayout();
	private final PaginationBarModern paginationBar;

	public AdvertisementsLayout(AdvertisementLeftSidebar sidebar, I18nService i18n) {
		this.paginationBar = new PaginationBarModern(i18n);

		setSizeFull();
		setSpacing(true);
		setPadding(true);

		VerticalLayout contentLayout = new VerticalLayout(advertisementContainer, paginationBar);
		contentLayout.setSizeFull();
		contentLayout.setSpacing(true);
		contentLayout.setPadding(false);
		contentLayout.setFlexGrow(1, advertisementContainer);

		add(sidebar, contentLayout);
		setFlexGrow(1, contentLayout);
	}

	public VerticalLayout getAdvertisementContainer() {
		return advertisementContainer;
	}

	public PaginationBarModern getPaginationBar() {
		return paginationBar;
	}
}
