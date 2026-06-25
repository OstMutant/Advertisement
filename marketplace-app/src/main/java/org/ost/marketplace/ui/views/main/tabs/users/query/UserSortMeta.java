package org.ost.marketplace.ui.views.main.tabs.users.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.marketplace.ui.query.sort.SortFieldMeta;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSortMeta {

    public static final SortFieldMeta ID         = SortFieldMeta.of("id",        USER_SORT_ID);
    public static final SortFieldMeta NAME       = SortFieldMeta.of("name",       USER_SORT_NAME);
    public static final SortFieldMeta EMAIL      = SortFieldMeta.of("email",      USER_SORT_EMAIL);
    public static final SortFieldMeta ROLE       = SortFieldMeta.of("role",       USER_SORT_ROLE);
    public static final SortFieldMeta CREATED_AT = SortFieldMeta.of("createdAt",  USER_SORT_CREATED);
    public static final SortFieldMeta UPDATED_AT = SortFieldMeta.of("updatedAt",  USER_SORT_UPDATED);
}
