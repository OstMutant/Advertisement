package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.List;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.ost.advertisement.mappers.AdvertisementMapper;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.components.PaginationBarModern;

@SpringComponent
@UIScope
public class AdvertisementsView extends VerticalLayout {

	private final AdvertisementService advertisementService;
	private final AdvertisementMapper mapper;
	private final AdvertisementLeftSidebar sidebar;
	private final VerticalLayout advertisementContainer = new VerticalLayout();
	private final PaginationBarModern paginationBar = new PaginationBarModern();
	private final I18nService i18n;

	public AdvertisementsView(AdvertisementService advertisementService, AdvertisementMapper mapper,
							  AdvertisementLeftSidebar sidebar, I18nService i18n) {
		this.mapper = mapper;
		this.advertisementService = advertisementService;
		this.sidebar = sidebar;
		this.i18n = i18n;
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
					() -> openAdvertisementFormDialog(ad),
					() -> openConfirmDeleteDialog(ad))
			)
		);
	}

	private void openAdvertisementFormDialog(AdvertisementView ad) {
		AdvertisementFormDialog dialog = new AdvertisementFormDialog(mapper.toAdvertisementEdit(ad),
			advertisementService, i18n);
		dialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				refreshAdvertisements();
			}
		});
		dialog.open();
	}

	private void openConfirmDeleteDialog(AdvertisementView ad) {
		Dialog dialog = new Dialog();
		String confirmText = i18n.get("advertisement.view.confirm.delete.text", ad.title(), ad.id());
		dialog.add(new Span(confirmText));

		Button confirm = new Button(i18n.get("advertisement.view.confirm.delete.button"), e -> {
			try {
				advertisementService.delete(ad);
				NotificationType.SUCCESS.show(i18n.get("advertisement.view.notification.deleted"));
				refreshAdvertisements();
			} catch (Exception ex) {
				NotificationType.ERROR.show(i18n.get("advertisement.view.notification.delete.error", ex.getMessage()));
			}
			dialog.close();
		});
		confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

		Button cancel = new Button(i18n.get("advertisement.view.confirm.cancel.button"), e -> dialog.close());
		cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

		dialog.getFooter().add(cancel, confirm);
		dialog.open();
	}
}
