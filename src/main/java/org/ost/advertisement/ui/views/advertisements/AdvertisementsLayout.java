package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import lombok.Getter;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.PaginationBarModern;

@Getter
public class AdvertisementsLayout extends HorizontalLayout {

	private final FlexLayout advertisementContainer = new FlexLayout();
	private final PaginationBarModern paginationBar;

	public AdvertisementsLayout(AdvertisementLeftSidebar sidebar, I18nService i18n) {
		this.paginationBar = new PaginationBarModern(i18n);

		setSizeFull();
		setSpacing(true);
		setPadding(true);

		advertisementContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
		advertisementContainer.setJustifyContentMode(JustifyContentMode.START);
		advertisementContainer.setAlignItems(Alignment.START);
		advertisementContainer.getStyle()
			.set("gap", "16px")
			.set("padding", "16px");

		VerticalLayout contentLayout = new VerticalLayout(advertisementContainer, paginationBar);
		contentLayout.setSizeFull();
		contentLayout.setSpacing(true);
		contentLayout.setPadding(false);
		contentLayout.setFlexGrow(1, advertisementContainer);

		add(sidebar, contentLayout);
		setFlexGrow(1, contentLayout);
	}
}
