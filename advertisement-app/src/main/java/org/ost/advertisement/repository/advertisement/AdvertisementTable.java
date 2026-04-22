package org.ost.advertisement.repository.advertisement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdvertisementTable {

    public static final String TABLE      = "advertisement";
    public static final String ALIAS      = "a";

    public static final String ID                  = ALIAS + ".id";
    public static final String TITLE              = ALIAS + ".title";
    public static final String DESCRIPTION        = ALIAS + ".description";
    public static final String CREATED_AT         = ALIAS + ".created_at";
    public static final String UPDATED_AT         = ALIAS + ".updated_at";
    public static final String CREATED_BY_USER_ID = ALIAS + ".created_by_user_id";
    public static final String DELETED_AT         = ALIAS + ".deleted_at";

    public static final String MAIN_IMAGE_URL = "att.main_image_url";
    public static final String IMAGE_COUNT    = "COALESCE(att.image_count, 0)";

    private static final String ATTACHMENT_JOIN =
            " LEFT JOIN (SELECT entity_id," +
            " COUNT(*) AS image_count," +
            " (array_agg(url ORDER BY created_at ASC))[1] AS main_image_url" +
            " FROM attachment WHERE entity_type = 'ADVERTISEMENT' AND deleted_at IS NULL GROUP BY entity_id) att" +
            " ON att.entity_id = " + ALIAS + ".id";

    public static final String SOURCE =
            TABLE + " " + ALIAS +
            " LEFT JOIN user_information u ON " + ALIAS + ".created_by_user_id = u.id" +
            ATTACHMENT_JOIN;
}