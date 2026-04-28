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

    public static final String ATTACHMENT_JOIN =
            "LEFT JOIN LATERAL (" +
            "  SELECT att.url AS att_url," +
            "         (SELECT COUNT(*) FROM attachment att2" +
            "          WHERE att2.entity_type = 'ADVERTISEMENT' AND att2.entity_id = " + ALIAS + ".id AND att2.deleted_at IS NULL) AS att_count" +
            "  FROM attachment att" +
            "  WHERE att.entity_type = 'ADVERTISEMENT' AND att.entity_id = " + ALIAS + ".id AND att.deleted_at IS NULL" +
            "  ORDER BY att.created_at ASC LIMIT 1" +
            ") att ON true";

    public static final String SOURCE =
            TABLE + " " + ALIAS +
            " LEFT JOIN user_information u ON " + ALIAS + ".created_by_user_id = u.id" +
            " " + ATTACHMENT_JOIN;

    /** Lightweight source for COUNT queries — filters use only advertisement columns. */
    public static final String COUNT_SOURCE = TABLE + " " + ALIAS;
}