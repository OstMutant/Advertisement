package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.List;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.dialogs.AdvertisementUpsertDialog;
import org.ost.advertisement.ui.views.components.filters.FilterFieldsProcessor;
import org.ost.advertisement.ui.views.components.sort.SortFieldsProcessor;
import org.springframework.beans.factory.ObjectProvider;

@SpringComponent
@UIScope
public class AdvertisementsView extends AdvertisementsLayout {

	private final transient AdvertisementService advertisementService;
	private final transient I18nService i18n;
	private final transient AdvertisementQueryBlock queryBlock;
	private final AdvertisementUpsertDialog upsertDialog;
	private final ObjectProvider<AdvertisementCardView> cardProvider;

	public AdvertisementsView(AdvertisementService advertisementService,
							  AdvertisementQueryBlock queryBlock,
							  AdvertisementUpsertDialog upsertDialog,
							  I18nService i18n, ObjectProvider<AdvertisementCardView> cardProvider) {
		super(queryBlock, i18n);
		this.advertisementService = advertisementService;
		this.queryBlock = queryBlock;
		this.upsertDialog = upsertDialog;
		this.upsertDialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				refreshAdvertisements();
			}
		});
		this.i18n = i18n;
		this.cardProvider = cardProvider;

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
			AdvertisementCardView card = cardProvider.getObject()
				.build(ad,
					() -> upsertDialog.openEdit(ad),
					this::refreshAdvertisements);
			getAdvertisementContainer().add(card);
		});

		getStatusBar().update(filterFieldsProcessor, sortFieldsProcessor);
	}
}

