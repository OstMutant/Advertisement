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
import java.util.List;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.mappers.AdvertisementMapper;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.ui.views.components.PaginationBarModern;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;

@SpringComponent
@Scope("prototype")
public class AdvertisementsView extends VerticalLayout {

	private final AdvertisementRepository repository;
	private final AdvertisementMapper advertisementMapper;
	private final PaginationBarModern paginationBar = new PaginationBarModern();
	private final AdvertisementLeftSidebar sidebar;
	private final AdvertisementFilterFields filterFields;
	private final AdvertisementSortFields sortFields;
	private final VerticalLayout advertisementContainer = new VerticalLayout();

	public AdvertisementsView(AdvertisementRepository repository, AdvertisementMapper advertisementMapper) {
		this.advertisementMapper = advertisementMapper;
		this.repository = repository;
		setSizeFull();
		setSpacing(true);
		setPadding(true);

		paginationBar.setPageSize(25);
		paginationBar.setPageChangeListener(e -> refreshAdvertisements());

		sidebar = new AdvertisementLeftSidebar(() -> {
			paginationBar.setTotalCount(0);
			refreshAdvertisements();
		}, () -> openAdvertisementFormDialog(null));

		filterFields = sidebar.getFilterFields();
		sortFields = sidebar.getSortFields();

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
		PageRequest pageable = PageRequest.of(page, size, sortFields.getOriginalSort().getSort());
		AdvertisementFilter originalFilter = filterFields.getOriginalFilter();
		List<AdvertisementView> pageData = repository.findByFilter(originalFilter, pageable);
		int totalCount = repository.countByFilter(originalFilter).intValue();

		paginationBar.setTotalCount(totalCount);
		advertisementContainer.removeAll();
		pageData.forEach(ad ->
			advertisementContainer.add(
				new AdvertisementCardView(ad,
					() -> openAdvertisementFormDialog(advertisementMapper.toEntity(ad)),
					() -> openConfirmDeleteDialog(ad))
			)
		);
	}

	private void openAdvertisementFormDialog(Advertisement advertisement) {
		AdvertisementFormDialog dialog = new AdvertisementFormDialog(advertisement, repository);
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
				repository.deleteById(ad.id());
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
