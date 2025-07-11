package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.entyties.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.ui.components.FloatingActionButton;

@SpringComponent
@UIScope
public class AdvertisementsView extends VerticalLayout {

	private final AdvertisementListView advertisementListView;
	private final AdvertisementRepository advertisementRepository;

	public AdvertisementsView(AdvertisementListView advertisementListView, AdvertisementRepository advertisementRepository) {
		this.advertisementListView = advertisementListView;
		this.advertisementRepository = advertisementRepository;

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
				advertisementListView.getDataProvider().refreshAll();
			}
		});
		dialog.open();
	}
}
