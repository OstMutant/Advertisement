package org.ost.attachment.repository;

import java.util.List;

/** One filename-list diff entry (before/after) persisted as JSON inside an {@code attachment_snapshot} row. */
public record AttachmentMediaChange(List<String> before, List<String> after, int schemaVersion) {

    public static final int SCHEMA_VERSION = 1;

    public AttachmentMediaChange(List<String> before, List<String> after) {
        this(before, after, SCHEMA_VERSION);
    }
}
