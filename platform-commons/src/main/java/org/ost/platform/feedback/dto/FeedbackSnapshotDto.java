package org.ost.platform.feedback.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.model.FeedbackModerationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.ost.platform.audit.api.AuditableSnapshot.diffField;
import static org.ost.platform.audit.api.AuditableSnapshot.enumName;
import static org.ost.platform.audit.api.AuditableSnapshot.field;
import static org.ost.platform.core.model.ChangeEntry.FieldChange;

/** Audit snapshot for a feedback entry, capturing moderation status and reason changes. */
@JsonTypeName("feedback")
@FieldNameConstants
public record FeedbackSnapshotDto(
        FeedbackModerationStatus moderationStatus,
        String moderationReason,
        int schemaVersion
) implements AuditableSnapshot {

    public static final int SCHEMA_VERSION = 1;

    public FeedbackSnapshotDto(FeedbackModerationStatus moderationStatus, String moderationReason) {
        this(moderationStatus, moderationReason, SCHEMA_VERSION);
    }

    @Override
    public EntityType entityType() { return EntityType.FEEDBACK; }

    @Override
    public Optional<String> displayName() { return Optional.empty(); }

    @Override
    public List<ChangeEntry> diff(AuditableSnapshot previous) {
        FeedbackSnapshotDto prev = previous instanceof FeedbackSnapshotDto p ? p : null;
        List<ChangeEntry> changes = new ArrayList<>();
        diffField(changes, Fields.moderationStatus, field(prev, FeedbackSnapshotDto::moderationStatus), moderationStatus());
        diffField(changes, Fields.moderationReason, field(prev, FeedbackSnapshotDto::moderationReason), moderationReason());
        return changes;
    }

    @Override
    public List<ChangeEntry.FieldChange> allFields() {
        return List.of(
                new FieldChange(Fields.moderationStatus, null, enumName(moderationStatus())),
                new FieldChange(Fields.moderationReason, null, moderationReason()));
    }
}
