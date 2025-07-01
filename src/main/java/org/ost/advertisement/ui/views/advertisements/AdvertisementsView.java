package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.entyties.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;

@SpringComponent
@UIScope
public class AdvertisementsView extends VerticalLayout {

	private final AdvertisementListView advertisementListView;
	private final AdvertisementRepository advertisementRepository;
	private Button addAdvertisementButton;

	public AdvertisementsView(AdvertisementListView advertisementListView, AdvertisementRepository advertisementRepository) {
		this.advertisementListView = advertisementListView;
		this.advertisementRepository = advertisementRepository;
		setSizeFull();

		addAdvertisementButton = new Button("Add Advertisement", VaadinIcon.PLUS.create());
		addAdvertisementButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		addAdvertisementButton.addClickListener(event -> openAdvertisementFormDialog(null));

		HorizontalLayout toolbar = new HorizontalLayout(addAdvertisementButton);
		toolbar.setWidthFull();
		toolbar.setJustifyContentMode(JustifyContentMode.END);

		add(toolbar, advertisementListView);
	}

	private void openAdvertisementFormDialog(Advertisement advertisement) {
		AdvertisementFormDialog dialog = new AdvertisementFormDialog(advertisement, advertisementRepository);
		dialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				advertisementListView.getDataProvider().refreshAll(); // Refresh the grid when dialog is closed
			}
		});
		dialog.open();
	}
}
