package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.advertisements.meta.AdvertisementSortMeta;
import org.ost.advertisement.ui.views.components.query.QueryStatusBar;
import org.springframework.beans.factory.ObjectProvider;

@SpringComponent
@UIScope
public class AdvertisementQueryStatusBlock {

	@Getter
	private final QueryStatusBar<AdvertisementFilterDto> statusBar;
	@Getter
	private final AdvertisementQueryBlock queryBlock;

	public AdvertisementQueryStatusBlock(I18nService i18n,
										 AdvertisementQueryBlock queryBlock,
										 ObjectProvider<QueryStatusBar<AdvertisementFilterDto>> statusBarProvider) {
		this.queryBlock = queryBlock;

		this.statusBar = statusBarProvider.getObject(i18n, queryBlock, queryBlock, AdvertisementSortMeta.labelProvider(i18n));

		statusBar.getElement().addEventListener("click", e -> statusBar.toggleVisibility());
		statusBar.getStyle().set("cursor", "pointer");
	}

}
