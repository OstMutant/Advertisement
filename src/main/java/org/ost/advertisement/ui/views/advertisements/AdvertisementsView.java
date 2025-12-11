package org.ost.advertisement.ui.views.advertisements;

import static com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.START;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SIDEBAR_BUTTON_ADD;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.List;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.dialogs.AdvertisementUpsertDialog;
import org.ost.advertisement.ui.views.components.PaginationBarModern;
import org.ost.advertisement.ui.views.components.query.filter.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.sort.SortProcessor;

@SpringComponent
@UIScope
public class AdvertisementsView extends VerticalLayout {

	private final transient AdvertisementService advertisementService;
	private final transient I18nService i18n;
	private final transient AdvertisementQueryStatusBar queryStatusBar;
	private final transient AdvertisementUpsertDialog.Builder upsertDialogBuilder;
	private final transient AdvertisementCardView.Builder cardBuilder;
	private final FlexLayout advertisementContainer = getAdvertisementContainer();
	private final PaginationBarModern paginationBar;
	private final Button addAdvertisementButton;

	public AdvertisementsView(AdvertisementService advertisementService,
							  AdvertisementQueryStatusBar queryStatusBar,
							  AdvertisementUpsertDialog.Builder upsertDialogBuilder,
							  I18nService i18n,
							  AdvertisementCardView.Builder cardBuilder) {
		this.advertisementService = advertisementService;
		this.queryStatusBar = queryStatusBar;
		this.upsertDialogBuilder = upsertDialogBuilder;
		this.i18n = i18n;
		this.cardBuilder = cardBuilder;
		this.paginationBar = new PaginationBarModern(i18n);

		this.addAdvertisementButton = new Button(i18n.get(ADVERTISEMENT_SIDEBAR_BUTTON_ADD));
		addAdvertisementButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		addAdvertisementButton.getStyle().set("margin-top", "12px");
		addAdvertisementButton.addClickListener(
			e -> this.upsertDialogBuilder.buildAndOpen(this::refreshAdvertisements));

		queryStatusBar.getQueryBlock().addEventListener(() -> {
			paginationBar.setTotalCount(0);
			refreshAdvertisements();
		});

		paginationBar.setPageChangeListener(e -> refreshAdvertisements());

		add(
			queryStatusBar,
			queryStatusBar.getQueryBlock().getLayout(),
			addAdvertisementButton,
			advertisementContainer,
			paginationBar
		);
		setFlexGrow(1, advertisementContainer);

		setSizeFull();
		setSpacing(false);
		setPadding(true);

		refreshAdvertisements();
	}

	private FlexLayout getAdvertisementContainer() {
		FlexLayout container = new FlexLayout();
		container.setFlexWrap(FlexLayout.FlexWrap.WRAP);
		container.setJustifyContentMode(START);
		container.setAlignItems(Alignment.START);
		container.getStyle()
			.set("gap", "16px")
			.set("padding", "16px");
		return container;
	}

	private void refreshAdvertisements() {
		AdvertisementQueryBlock queryBlock = queryStatusBar.getQueryBlock();
		FilterProcessor<AdvertisementFilterDto> filterProcessor = queryBlock.getFilterProcessor();
		SortProcessor sortProcessor = queryBlock.getSortProcessor();

		int page = paginationBar.getCurrentPage();
		int size = paginationBar.getPageSize();

		AdvertisementFilterDto filter = filterProcessor.getOriginalFilter();

		List<AdvertisementInfoDto> ads = advertisementService.getFiltered(
			filter,
			page,
			size,
			sortProcessor.getOriginalSort().getSort()
		);

		paginationBar.setTotalCount(advertisementService.count(filter));

		advertisementContainer.removeAll();
		ads.forEach(ad -> advertisementContainer.add(cardBuilder.build(ad, this::refreshAdvertisements)));

		queryStatusBar.update(filterProcessor, sortProcessor);
	}
}
