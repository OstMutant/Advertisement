package org.ost.advertisement.ui.views.advertisements.query.elements;

import lombok.Getter;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.QueryStatusBar;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
@Getter
public class AdvertisementQueryStatusBar extends QueryStatusBar<AdvertisementFilterDto> {

    private final transient AdvertisementQueryBlock queryBlock;

    public AdvertisementQueryStatusBar(I18nService i18n, AdvertisementQueryBlock queryBlock) {
        super(i18n, queryBlock, queryBlock);
        this.queryBlock = queryBlock;
    }
}
