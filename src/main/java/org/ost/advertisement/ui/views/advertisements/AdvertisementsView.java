package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_VIEW_NOTIFICATION_DELETED;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.List;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmDeleteHelper;

@SpringComponent
@UIScope
public class AdvertisementsView extends VerticalLayout {

	private final transient AdvertisementService advertisementService;
	private final transient AdvertisementMapper mapper;
	private final transient I18nService i18n;
	private final AdvertisementLeftSidebar sidebar;
	private final AdvertisementsLayout layout;

	public AdvertisementsView(AdvertisementService advertisementService, AdvertisementMapper mapper,
							  AdvertisementLeftSidebar sidebar, I18nService i18n) {
		this.mapper = mapper;
		this.advertisementService = advertisementService;
		this.sidebar = sidebar;
		this.i18n = i18n;
		this.layout = new AdvertisementsLayout(sidebar, i18n);

		addClassName("advertisement-list-view");
		setSizeFull();
		setSpacing(false);
		setPadding(false);

		layout.getPaginationBar().setPageChangeListener(e -> refreshAdvertisements());

		sidebar.eventProcessor(() -> {
			layout.getPaginationBar().setTotalCount(0);
			refreshAdvertisements();
		}, () -> openAdvertisementFormDialog(null));

		add(layout);
		refreshAdvertisements();
	}

	private void refreshAdvertisements() {
		int page = layout.getPaginationBar().getCurrentPage();
		int size = layout.getPaginationBar().getPageSize();
		AdvertisementFilterDto originalFilter = sidebar.getFilterFields().getFilterFieldsProcessor()
			.getOriginalFilter();
		List<AdvertisementInfoDto> pageData = advertisementService.getFiltered(originalFilter, page, size,
			sidebar.getSortFields().getSortFieldsProcessor().getOriginalSort().getSort());
		int totalCount = advertisementService.count(originalFilter);

		layout.getPaginationBar().setTotalCount(totalCount);
		layout.getAdvertisementContainer().removeAll();
		pageData.forEach(ad ->
			layout.getAdvertisementContainer().add(
				new AdvertisementCardView(ad,
					() -> openAdvertisementFormDialog(ad),
					() -> openConfirmDeleteDialog(ad),
					i18n))
		);
	}

	private void openAdvertisementFormDialog(AdvertisementInfoDto ad) {
		AdvertisementFormDialog dialog = new AdvertisementFormDialog(mapper.toAdvertisementEdit(ad),
			advertisementService, i18n, mapper);
		dialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				refreshAdvertisements();
			}
		});
		dialog.open();
	}

	private void openConfirmDeleteDialog(AdvertisementInfoDto ad) {
		ConfirmDeleteHelper.showConfirm(
			i18n,
			i18n.get(ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT, ad.title(), ad.id()),
			ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON,
			ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON,
			() -> {
				try {
					advertisementService.delete(ad);
					NotificationType.SUCCESS.show(i18n.get(ADVERTISEMENT_VIEW_NOTIFICATION_DELETED));
					refreshAdvertisements();
				} catch (Exception ex) {
					NotificationType.ERROR.show(
						i18n.get(ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage()));
				}
			}
		);
	}
}
