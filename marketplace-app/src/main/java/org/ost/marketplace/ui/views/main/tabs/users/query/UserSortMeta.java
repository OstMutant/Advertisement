package org.ost.marketplace.ui.views.main.tabs.users.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.marketplace.ui.query.sort.SortFieldMeta;
import org.ost.platform.user.dto.UserDto;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSortMeta {

    public static final SortFieldMeta ID         = SortFieldMeta.of(UserDto.Fields.id,        USER_SORT_ID);
    public static final SortFieldMeta NAME       = SortFieldMeta.of(UserDto.Fields.name,       USER_SORT_NAME);
    public static final SortFieldMeta EMAIL      = SortFieldMeta.of(UserDto.Fields.email,      USER_SORT_EMAIL);
    public static final SortFieldMeta ROLE       = SortFieldMeta.of(UserDto.Fields.role,       USER_SORT_ROLE);
    public static final SortFieldMeta CREATED_AT = SortFieldMeta.of(UserDto.Fields.createdAt,  USER_SORT_CREATED);
    public static final SortFieldMeta UPDATED_AT = SortFieldMeta.of(UserDto.Fields.updatedAt,  USER_SORT_UPDATED);
}
