package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementSortMeta;
import org.ost.advertisement.ui.views.components.QueryStatusBar;

@SpringComponent
@UIScope
public class AdvertisementQueryStatusBlock {

	@Getter
	private final QueryStatusBar statusBar;
	@Getter
	private final AdvertisementQueryBlock queryBlock;

	public AdvertisementQueryStatusBlock(I18nService i18n, AdvertisementQueryBlock queryBlock) {
		this.queryBlock = queryBlock;

		this.statusBar = new QueryStatusBar(
			i18n,
			queryBlock.getFilterProcessor(),
			queryBlock.getSortProcessor(),
			AdvertisementSortMeta.labelProvider(i18n)
		);

		statusBar.getElement().addEventListener("click", e ->
			queryBlock.getLayout().setVisible(!queryBlock.getLayout().isVisible())
		);
		statusBar.getStyle().set("cursor", "pointer");
	}
}
