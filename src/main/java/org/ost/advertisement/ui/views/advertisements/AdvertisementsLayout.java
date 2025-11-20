package org.ost.advertisement.ui.views.advertisements;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SIDEBAR_BUTTON_ADD;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import lombok.Getter;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementSortMeta;
import org.ost.advertisement.ui.views.components.PaginationBarModern;
import org.ost.advertisement.ui.views.components.QueryStatusBar;

@Getter
public class AdvertisementsLayout extends VerticalLayout {

	private final QueryStatusBar statusBar;
	private final AdvertisementQueryBlock queryBlock;
	private final Button addAdvertisementButton;
	private final FlexLayout advertisementContainer = new FlexLayout();
	private final PaginationBarModern paginationBar;

	public AdvertisementsLayout(AdvertisementQueryBlock queryBlock, I18nService i18n) {
		this.queryBlock = queryBlock;

		this.statusBar = new QueryStatusBar(
			i18n,
			queryBlock.getFilterProcessor(),
			queryBlock.getSortProcessor(),
			AdvertisementSortMeta.labelProvider(i18n)
		);

		this.addAdvertisementButton = new Button(i18n.get(ADVERTISEMENT_SIDEBAR_BUTTON_ADD));
		addAdvertisementButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		addAdvertisementButton.getStyle().set("margin-top", "12px");

		setSizeFull();
		setSpacing(false);
		setPadding(true);

		advertisementContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
		advertisementContainer.setJustifyContentMode(FlexLayout.JustifyContentMode.START);
		advertisementContainer.setAlignItems(FlexLayout.Alignment.START);
		advertisementContainer.getStyle()
			.set("gap", "16px")
			.set("padding", "16px");

		statusBar.getElement().addEventListener("click", e ->
			queryBlock.getLayout().setVisible(!queryBlock.getLayout().isVisible())
		);
		statusBar.getStyle().set("cursor", "pointer");

		this.paginationBar = new PaginationBarModern(i18n);

		add(
			statusBar,
			queryBlock.getLayout(),
			addAdvertisementButton,
			advertisementContainer,
			paginationBar
		);
		setFlexGrow(1, advertisementContainer);
	}

	public void eventProcessor(Runnable onRefreshAction, Runnable onAddButton) {
		addAdvertisementButton.addClickListener(e -> onAddButton.run());
		queryBlock.eventProcessor(onRefreshAction);
	}
}
