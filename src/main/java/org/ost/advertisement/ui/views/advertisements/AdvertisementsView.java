package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_VIEW_NOTIFICATION_DELETED;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR;

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
public class AdvertisementsView extends AdvertisementsLayout {

	private final transient AdvertisementService advertisementService;
	private final transient AdvertisementMapper mapper;
	private final transient I18nService i18n;
	private final AdvertisementLeftSidebar sidebar;

	public AdvertisementsView(AdvertisementService advertisementService,
							  AdvertisementMapper mapper,
							  AdvertisementLeftSidebar sidebar,
							  I18nService i18n) {
		super(sidebar, i18n);
		this.advertisementService = advertisementService;
		this.mapper = mapper;
		this.sidebar = sidebar;
		this.i18n = i18n;

		getPaginationBar().setPageChangeListener(e -> refreshAdvertisements());

		sidebar.eventProcessor(
			() -> {
				getPaginationBar().setTotalCount(0);
				refreshAdvertisements();
			},
			() -> openAdvertisementFormDialog(null)
		);

		refreshAdvertisements();
	}

	private void refreshAdvertisements() {
		int page = getPaginationBar().getCurrentPage();
		int size = getPaginationBar().getPageSize();

		AdvertisementFilterDto filter = sidebar.getQueryBlock()
			.getFilterProcessor()
			.getOriginalFilter();

		List<AdvertisementInfoDto> ads = advertisementService.getFiltered(
			filter,
			page,
			size,
			sidebar.getQueryBlock()
				.getSortProcessor()
				.getOriginalSort()
				.getSort()
		);

		int totalCount = advertisementService.count(filter);
		getPaginationBar().setTotalCount(totalCount);

		getAdvertisementContainer().removeAll();
		ads.forEach(ad -> {
			AdvertisementCardView card = new AdvertisementCardView(
				ad,
				() -> openAdvertisementFormDialog(ad),
				() -> openConfirmDeleteDialog(ad),
				i18n
			);
			getAdvertisementContainer().add(card);
		});
	}

	private void openAdvertisementFormDialog(AdvertisementInfoDto ad) {
		AdvertisementFormDialog dialog = new AdvertisementFormDialog(
			mapper.toAdvertisementEdit(ad),
			advertisementService,
			i18n,
			mapper
		);
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
			i18n.get(ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT, ad.getTitle(), ad.getId()),
			ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON,
			ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON,
			() -> {
				try {
					advertisementService.delete(ad);
					NotificationType.SUCCESS.show(i18n.get(ADVERTISEMENT_VIEW_NOTIFICATION_DELETED));
					refreshAdvertisements();
				} catch (Exception ex) {
					NotificationType.ERROR.show(
						i18n.get(ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage())
					);
				}
			}
		);
	}
}

