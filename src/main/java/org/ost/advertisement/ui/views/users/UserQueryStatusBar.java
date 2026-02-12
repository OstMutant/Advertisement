package org.ost.advertisement.ui.views.users;

import lombok.Getter;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.QueryStatusBar;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
@Getter
public class UserQueryStatusBar extends QueryStatusBar<UserFilterDto> {

    private final transient UserQueryBlock queryBlock;

    public UserQueryStatusBar(I18nService i18n, UserQueryBlock queryBlock) {
        super(i18n, queryBlock, queryBlock);
        this.queryBlock = queryBlock;
    }
}

