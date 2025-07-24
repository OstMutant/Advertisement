package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.repository.AdvertisementRepository;

public class AdvertisementsView extends VerticalLayout {

	public AdvertisementsView(AdvertisementRepository repository) {
		AdvertisementListView listView = new AdvertisementListView(repository);

		setSizeFull();
		setPadding(false);
		setSpacing(false);

		add(listView);
	}
}
