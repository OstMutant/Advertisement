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
	private final transient ObjectProvider<AdvertisementUpsertDialog> upsertDialogProvider;
	private final transient ObjectProvider<AdvertisementCardView> cardProvider;

	public AdvertisementsView(AdvertisementService advertisementService,
							  AdvertisementQueryBlock queryBlock,
							  ObjectProvider<AdvertisementUpsertDialog> upsertDialogProvider,
							  I18nService i18n, ObjectProvider<AdvertisementCardView> cardProvider) {
		super(queryBlock, i18n);
		this.advertisementService = advertisementService;
		this.queryBlock = queryBlock;
		this.upsertDialogProvider = upsertDialogProvider;
		this.i18n = i18n;
		this.cardProvider = cardProvider;

		getPaginationBar().setPageChangeListener(e -> refreshAdvertisements());

		eventProcessor(
			() -> {
				getPaginationBar().setTotalCount(0);
				refreshAdvertisements();
			},
			() -> upsertDialogProvider.getObject().openNew(this::refreshAdvertisements)
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
			AdvertisementCardView card = cardProvider.getObject().build(ad, this::refreshAdvertisements);
			getAdvertisementContainer().add(card);
		});

		getStatusBar().update(filterFieldsProcessor, sortFieldsProcessor);
	}
}

