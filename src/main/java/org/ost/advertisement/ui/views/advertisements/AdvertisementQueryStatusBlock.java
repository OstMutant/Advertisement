package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementSortMeta;
import org.ost.advertisement.ui.views.components.QueryStatusBar;

@SpringComponent
@UIScope
public class AdvertisementQueryStatusBlock {

	@Getter
	private final QueryStatusBar<AdvertisementFilterDto> statusBar;
	@Getter
	private final AdvertisementQueryBlock queryBlock;

	public AdvertisementQueryStatusBlock(I18nService i18n, AdvertisementQueryBlock queryBlock) {
		this.queryBlock = queryBlock;

		this.statusBar = new QueryStatusBar<>(
			i18n,
			queryBlock,
			AdvertisementSortMeta.labelProvider(i18n)
		);

		statusBar.getElement().addEventListener("click", e -> statusBar.toggle());
		statusBar.getStyle().set("cursor", "pointer");
	}

}
