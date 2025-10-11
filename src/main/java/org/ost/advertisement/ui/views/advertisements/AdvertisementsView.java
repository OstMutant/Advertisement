package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_VIEW_NOTIFICATION_DELETED;
import static org.ost.advertisement.constans.I18nKey.ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.List;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.ui.mappers.AdvertisementMapper;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.components.PaginationBarModern;

@SpringComponent
@UIScope
public class AdvertisementsView extends VerticalLayout {

	private final transient AdvertisementService advertisementService;
	private final transient AdvertisementMapper mapper;
	private final AdvertisementLeftSidebar sidebar;
	private final VerticalLayout advertisementContainer = new VerticalLayout();
	private final PaginationBarModern paginationBar;
	private final transient I18nService i18n;

	public AdvertisementsView(AdvertisementService advertisementService, AdvertisementMapper mapper,
							  AdvertisementLeftSidebar sidebar, I18nService i18n) {
		this.mapper = mapper;
		this.advertisementService = advertisementService;
		this.sidebar = sidebar;
		this.i18n = i18n;
		this.paginationBar = new PaginationBarModern(i18n);
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
		AdvertisementFilterDto originalFilter = sidebar.getFilterFields().getFilterFieldsProcessor().getOriginalFilter();
		List<AdvertisementInfoDto> pageData = advertisementService.getFiltered(originalFilter, page, size,
			sidebar.getSortFields().getSortFieldsProcessor().getOriginalSort().getSort());
		int totalCount = advertisementService.count(originalFilter);

		paginationBar.setTotalCount(totalCount);
		advertisementContainer.removeAll();
		pageData.forEach(ad ->
			advertisementContainer.add(
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
		Dialog dialog = new Dialog();
		String confirmText = i18n.get(ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT, ad.title(), ad.id());
		dialog.add(new Span(confirmText));

		Button confirm = new Button(i18n.get(ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON), e -> {
			try {
				advertisementService.delete(ad);
				NotificationType.SUCCESS.show(i18n.get(ADVERTISEMENT_VIEW_NOTIFICATION_DELETED));
				refreshAdvertisements();
			} catch (Exception ex) {
				NotificationType.ERROR.show(i18n.get(ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR, ex.getMessage()));
			}
			dialog.close();
		});
		confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

		Button cancel = new Button(i18n.get(ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON), e -> dialog.close());
		cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

		dialog.getFooter().add(cancel, confirm);
		dialog.open();
	}
}
