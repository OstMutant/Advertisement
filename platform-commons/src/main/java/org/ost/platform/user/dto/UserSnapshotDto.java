package org.ost.platform.user.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.ost.platform.audit.api.AuditableSnapshot.diffField;
import static org.ost.platform.audit.api.AuditableSnapshot.field;
import static org.ost.platform.core.model.ChangeEntry.FieldChange;

@JsonTypeName("user")
@FieldNameConstants
public record UserSnapshotDto(
        String name,
        String email,
        String role
) implements AuditableSnapshot {

    @Override
    public EntityType entityType() { return EntityType.USER; }

    @Override
    public Optional<String> displayName() { return Optional.of(name()); }

    @Override
    public List<ChangeEntry> diff(AuditableSnapshot previous) {
        UserSnapshotDto prev = previous instanceof UserSnapshotDto p ? p : null;
        List<ChangeEntry> changes = new ArrayList<>();
        diffField(changes, Fields.name,  field(prev, UserSnapshotDto::name),  name());
        diffField(changes, Fields.email, field(prev, UserSnapshotDto::email), email());
        diffField(changes, Fields.role,  field(prev, UserSnapshotDto::role),  role());
        return changes;
    }

    @Override
    public List<FieldChange> allFields() {
        return List.of(
                new FieldChange(Fields.name,  null, name()),
                new FieldChange(Fields.email, null, email()),
                new FieldChange(Fields.role,  null, role()));
    }
}
