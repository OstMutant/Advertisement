package org.ost.attachment.repository;

import org.ost.sqlengine.projection.SqlSelectField;

import static org.ost.sqlengine.projection.SqlSelectFieldFactory.*;

public final class PhotoSnapshotDescriptor {

    public static final String TABLE  = "photo_snapshot";
    public static final String ALIAS  = "ps";
    public static final String SOURCE = TABLE + " " + ALIAS;

    public static final SqlSelectField<Long>   ID               = longVal(ALIAS + ".id",               "ps_id");
    public static final SqlSelectField<Long>   ADVERTISEMENT_ID = longVal(ALIAS + ".advertisement_id", "advertisement_id");
    public static final SqlSelectField<String> CHANGES_SUMMARY  = str(ALIAS + ".changes_summary",  "changes_summary");
    public static final SqlSelectField<Long>   CHANGED_BY_USER_ID = longVal(ALIAS + ".changed_by_user_id", "changed_by_user_id");

    public static final class Write {
        private Write() {}
        public static final String TABLE              = PhotoSnapshotDescriptor.TABLE;
        public static final String ADVERTISEMENT_ID   = PhotoSnapshotDescriptor.ADVERTISEMENT_ID.columnName();
        public static final String VERSION            = "version";
        public static final String ATTACHMENT_URLS    = "attachment_urls";
        public static final String CHANGES_SUMMARY    = PhotoSnapshotDescriptor.CHANGES_SUMMARY.columnName();
        public static final String CHANGED_BY_USER_ID = PhotoSnapshotDescriptor.CHANGED_BY_USER_ID.columnName();
    }

    private PhotoSnapshotDescriptor() {}
}
