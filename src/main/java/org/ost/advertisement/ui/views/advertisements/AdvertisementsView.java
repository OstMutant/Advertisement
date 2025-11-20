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
import org.ost.advertisement.ui.views.advertisements.dialogs.AdvertisementFormDialog;
import org.ost.advertisement.ui.views.components.dialogs.ConfirmDeleteHelper;
import org.ost.advertisement.ui.views.components.filters.FilterFieldsProcessor;
import org.ost.advertisement.ui.views.components.sort.SortFieldsProcessor;

@SpringComponent
@UIScope
public class AdvertisementsView extends AdvertisementsLayout {

	private final transient AdvertisementService advertisementService;
	private final transient AdvertisementMapper mapper;
	private final transient I18nService i18n;
	private final transient AdvertisementQueryBlock queryBlock;
	private final AdvertisementFormDialog upsertDialog;

	public AdvertisementsView(AdvertisementService advertisementService,
							  AdvertisementMapper mapper,
							  AdvertisementQueryBlock queryBlock,
							  AdvertisementFormDialog upsertDialog,
							  I18nService i18n) {
		super(queryBlock, i18n);
		this.advertisementService = advertisementService;
		this.mapper = mapper;
		this.queryBlock = queryBlock;
		this.upsertDialog = upsertDialog;
		this.upsertDialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				refreshAdvertisements();
			}
		});
		this.i18n = i18n;

		getPaginationBar().setPageChangeListener(e -> refreshAdvertisements());

		eventProcessor(
			() -> {
				getPaginationBar().setTotalCount(0);
				refreshAdvertisements();
			},
			upsertDialog::open
		);

		refreshAdvertisements();
	}

	private void refreshAdvertisements() {
		FilterFieldsProcessor<AdvertisementFilterDto> filterFieldsProcessor = queryBlock.getFilterProcessor();
		SortFieldsProcessor sortFieldsProcessor = queryBlock.getSortProcessor();

		int page = getPaginationBar().getCurrentPage();
		int size = getPaginationBar().getPageSize();

		AdvertisementFilterDto filter = filterFieldsProcessor.getOriginalFilter();

		List<AdvertisementInfoDto> ads = advertisementService.getFiltered(
			filter,
			page,
			size,
			sortFieldsProcessor.getOriginalSort().getSort()
		);

		int totalCount = advertisementService.count(filter);
		getPaginationBar().setTotalCount(totalCount);

		getAdvertisementContainer().removeAll();
		ads.forEach(ad -> {
			AdvertisementCardView card = new AdvertisementCardView(
				ad,
				() -> upsertDialog.openEdit(mapper.toAdvertisementEdit(ad)),
				() -> openConfirmDeleteDialog(ad),
				i18n
			);
			getAdvertisementContainer().add(card);
		});

		getStatusBar().update(filterFieldsProcessor, sortFieldsProcessor);
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

