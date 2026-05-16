package org.ost.attachment.repository;

import org.ost.sqlengine.projection.SqlSelectField;

import java.time.Instant;

import static org.ost.sqlengine.projection.SqlSelectFieldFactory.*;

public final class AttachmentSnapshotDescriptor {

    public static final String TABLE  = "attachment_snapshot";
    public static final String ALIAS  = "ps";
    public static final String SOURCE = TABLE + " " + ALIAS;

    public static final SqlSelectField<Long>    ID                 = longVal(ALIAS + ".id",                "ps_id");
    public static final SqlSelectField<Long>    ADVERTISEMENT_ID   = longVal(ALIAS + ".advertisement_id",  "advertisement_id");
    public static final SqlSelectField<String>  CHANGES_SUMMARY    = str(ALIAS + ".changes_summary",   "changes_summary");
    public static final SqlSelectField<Long>    CHANGED_BY_USER_ID = longVal(ALIAS + ".changed_by_user_id", "changed_by_user_id");
    public static final SqlSelectField<Instant> CREATED_AT         = instant(ALIAS + ".created_at",    "created_at");

    public static final class Write {
        private Write() {}
        public static final String TABLE              = AttachmentSnapshotDescriptor.TABLE;
        public static final String ADVERTISEMENT_ID   = AttachmentSnapshotDescriptor.ADVERTISEMENT_ID.columnName();
        public static final String ATTACHMENT_URLS    = "attachment_urls";
        public static final String CHANGES_SUMMARY    = AttachmentSnapshotDescriptor.CHANGES_SUMMARY.columnName();
        public static final String CHANGED_BY_USER_ID = AttachmentSnapshotDescriptor.CHANGED_BY_USER_ID.columnName();
    }

    private AttachmentSnapshotDescriptor() {}
}
