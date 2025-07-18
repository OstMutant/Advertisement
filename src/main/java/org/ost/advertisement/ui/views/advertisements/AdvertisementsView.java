package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.entyties.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.ui.components.FloatingActionButton;

public class AdvertisementsView extends VerticalLayout {

	private final AdvertisementListView advertisementListView;
	private final AdvertisementRepository advertisementRepository;

	public AdvertisementsView(AdvertisementRepository advertisementRepository) {
		this.advertisementRepository = advertisementRepository;
		this.advertisementListView = new AdvertisementListView(advertisementRepository);

		setSizeFull();
		setPadding(false);
		setSpacing(false);

		add(advertisementListView);
		add(new FloatingActionButton(com.vaadin.flow.component.icon.VaadinIcon.PLUS, "Add Advertisement", e ->
			openAdvertisementFormDialog(null)
		));
	}

	private void openAdvertisementFormDialog(Advertisement advertisement) {
		AdvertisementFormDialog dialog = new AdvertisementFormDialog(advertisement, advertisementRepository);
		dialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				advertisementListView.refreshGrid();
			}
		});
		dialog.open();
	}
}
