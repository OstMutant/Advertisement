package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.repository.AdvertisementRepository;

public class AdvertisementsView extends VerticalLayout {

	public AdvertisementsView(AdvertisementRepository repository) {
		setSizeFull();
		setPadding(false);
		setSpacing(false);

		add(new AdvertisementListView(repository));
	}
}
