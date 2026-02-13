package org.ost.advertisement.ui.views.users.query.sort.meta;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.ui.views.components.query.sort.meta.SortFieldMeta;
import org.springframework.data.domain.Sort;

import static org.ost.advertisement.constants.I18nKey.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSortMeta {
    public static final SortFieldMeta ID = SortFieldMeta.of(User.Fields.id, USER_SORT_ID);
    public static final SortFieldMeta NAME = SortFieldMeta.of(User.Fields.name, USER_SORT_NAME);
    public static final SortFieldMeta EMAIL = SortFieldMeta.of(User.Fields.email, USER_SORT_EMAIL);
    public static final SortFieldMeta ROLE = SortFieldMeta.of(User.Fields.role, USER_SORT_ROLE);
    public static final SortFieldMeta CREATED_AT = SortFieldMeta.of(User.Fields.createdAt, USER_SORT_CREATED);
    public static final SortFieldMeta UPDATED_AT = SortFieldMeta.of(User.Fields.updatedAt, USER_SORT_UPDATED);

    public static CustomSort defaultSort() {
        return new CustomSort(Sort.by(
                Sort.Order.desc(UPDATED_AT.property()),
                Sort.Order.desc(CREATED_AT.property())
        ));
    }
}
