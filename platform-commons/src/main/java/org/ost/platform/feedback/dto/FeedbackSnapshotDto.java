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
import static org.ost.platform.audit.api.AuditableSnapshot.field;
import static org.ost.platform.core.model.ChangeEntry.FieldChange;

@JsonTypeName("feedback")
@FieldNameConstants
public record FeedbackSnapshotDto(
        FeedbackModerationStatus moderationStatus,
        int schemaVersion
) implements AuditableSnapshot {

    public static final int SCHEMA_VERSION = 1;

    public FeedbackSnapshotDto(FeedbackModerationStatus moderationStatus) {
        this(moderationStatus, SCHEMA_VERSION);
    }

    @Override
    public EntityType entityType() { return EntityType.FEEDBACK; }

    @Override
    public Optional<String> displayName() { return Optional.empty(); }

    @Override
    public List<ChangeEntry> diff(AuditableSnapshot previous) {
        FeedbackSnapshotDto prev = previous instanceof FeedbackSnapshotDto p ? p : null;
        List<ChangeEntry> changes = new ArrayList<>();
        diffField(changes, Fields.moderationStatus, typeToString(field(prev, FeedbackSnapshotDto::moderationStatus)), typeToString(moderationStatus()));
        return changes;
    }

    @Override
    public List<ChangeEntry.FieldChange> allFields() {
        return List.of(new FieldChange(Fields.moderationStatus, null, typeToString(moderationStatus())));
    }

    private static String typeToString(FeedbackModerationStatus status) {
        return status == null ? "" : status.name();
    }
}
