package org.ost.attachment.repository;

import org.ost.sqlengine.projection.SqlFieldDefinition;

import static org.ost.sqlengine.projection.SqlFieldBuilder.*;

public final class PhotoSnapshotProjection {

    public static final String TABLE  = "photo_snapshot";
    public static final String ALIAS  = "ps";
    public static final String SOURCE = TABLE + " " + ALIAS;

    public static final SqlFieldDefinition<Long>   ID               = id(ALIAS + ".id",               "ps_id");
    public static final SqlFieldDefinition<Long>   ADVERTISEMENT_ID = longVal(ALIAS + ".advertisement_id", "advertisement_id");
    public static final SqlFieldDefinition<String> CHANGES_SUMMARY  = str(ALIAS + ".changes_summary",  "changes_summary");
    public static final SqlFieldDefinition<Long>   CHANGED_BY_USER_ID = longVal(ALIAS + ".changed_by_user_id", "changed_by_user_id");

    public static final class Write {
        private Write() {}
        public static final String TABLE              = PhotoSnapshotProjection.TABLE;
        public static final String ADVERTISEMENT_ID   = PhotoSnapshotProjection.ADVERTISEMENT_ID.columnName();
        public static final String VERSION            = "version";
        public static final String ATTACHMENT_URLS    = "attachment_urls";
        public static final String CHANGES_SUMMARY    = PhotoSnapshotProjection.CHANGES_SUMMARY.columnName();
        public static final String CHANGED_BY_USER_ID = PhotoSnapshotProjection.CHANGED_BY_USER_ID.columnName();
    }

    private PhotoSnapshotProjection() {}
}
