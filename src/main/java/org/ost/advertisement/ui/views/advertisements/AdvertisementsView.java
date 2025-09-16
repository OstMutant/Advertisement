package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.List;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.mappers.AdvertisementMapper;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.ui.views.components.PaginationBarModern;

@SpringComponent
@UIScope
public class AdvertisementsView extends VerticalLayout {

	private final AdvertisementService advertisementService;
	private final AdvertisementMapper mapper;
	private final AdvertisementLeftSidebar sidebar;
	private final VerticalLayout advertisementContainer = new VerticalLayout();
	private final PaginationBarModern paginationBar = new PaginationBarModern();

	public AdvertisementsView(AdvertisementService advertisementService, AdvertisementMapper mapper,
							  AdvertisementLeftSidebar sidebar) {
		this.mapper = mapper;
		this.advertisementService = advertisementService;
		this.sidebar = sidebar;
		addClassName("advertisement-list-view");

		setSizeFull();
		setSpacing(true);
		setPadding(true);

		paginationBar.setPageChangeListener(e -> refreshAdvertisements());

		sidebar.eventProcessor(() -> {
			paginationBar.setTotalCount(0);
			refreshAdvertisements();
		}, () -> openAdvertisementFormDialog(null));

		VerticalLayout contentLayout = new VerticalLayout(advertisementContainer, paginationBar);
		contentLayout.setSizeFull();
		contentLayout.setSpacing(true);
		contentLayout.setPadding(false);
		contentLayout.setFlexGrow(1, advertisementContainer);

		HorizontalLayout mainLayout = new HorizontalLayout(sidebar, contentLayout);
		mainLayout.setSizeFull();
		mainLayout.setFlexGrow(1, contentLayout);

		add(mainLayout);
		refreshAdvertisements();
	}

	private void refreshAdvertisements() {
		int page = paginationBar.getCurrentPage();
		int size = paginationBar.getPageSize();
		AdvertisementFilter originalFilter = sidebar.getFilterFields().getFilterFieldsProcessor().getOriginalFilter();
		List<AdvertisementView> pageData = advertisementService.getFiltered(originalFilter, page, size,
			sidebar.getSortFields().getSortFieldsProcessor().getOriginalSort().getSort());
		int totalCount = advertisementService.count(originalFilter);

		paginationBar.setTotalCount(totalCount);
		advertisementContainer.removeAll();
		pageData.forEach(ad ->
			advertisementContainer.add(
				new AdvertisementCardView(ad,
					() -> openAdvertisementFormDialog(mapper.toAdvertisement(ad)),
					() -> openConfirmDeleteDialog(ad))
			)
		);
	}

	private void openAdvertisementFormDialog(Advertisement advertisement) {
		AdvertisementFormDialog dialog = new AdvertisementFormDialog(advertisement, advertisementService);
		dialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				refreshAdvertisements();
			}
		});
		dialog.open();
	}

	private void openConfirmDeleteDialog(AdvertisementView ad) {
		Dialog dialog = new Dialog();
		dialog.add(new Span("Delete advertisement \"" + ad.title() + "\" (ID " + ad.id() + ")?"));

		Button confirm = new Button("Delete", e -> {
			try {
				advertisementService.delete(ad);
				Notification.show("Advertisement deleted", 3000, Notification.Position.BOTTOM_START)
					.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
				refreshAdvertisements();
			} catch (Exception ex) {
				Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.BOTTOM_START)
					.addThemeVariants(NotificationVariant.LUMO_ERROR);
			}
			dialog.close();
		});
		confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

		Button cancel = new Button("Cancel", e -> dialog.close());
		cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

		dialog.getFooter().add(cancel, confirm);
		dialog.open();
	}
}
