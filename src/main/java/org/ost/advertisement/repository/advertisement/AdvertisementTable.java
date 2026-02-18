package org.ost.advertisement.repository.advertisement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdvertisementTable {

    public static final String TABLE      = "advertisement";
    public static final String ALIAS      = "a";

    public static final String ID          = ALIAS + ".id";
    public static final String TITLE       = ALIAS + ".title";
    public static final String DESCRIPTION = ALIAS + ".description";
    public static final String CREATED_AT  = ALIAS + ".created_at";
    public static final String UPDATED_AT  = ALIAS + ".updated_at";

    public static final String SOURCE =
            TABLE + " " + ALIAS + " LEFT JOIN user_information u ON " + ALIAS + ".created_by_user_id = u.id";
}