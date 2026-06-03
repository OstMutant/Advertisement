package org.ost.marketplace.dto.audit;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.experimental.FieldNameConstants;
import org.ost.marketplace.entities.User;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.ost.platform.audit.api.AuditableSnapshot.field;
import static org.ost.platform.core.model.ChangeEntry.FieldChange;

@JsonTypeName("user")
@FieldNameConstants
public record UserSnapshotDto(
        String name,
        String email,
        String role
) implements AuditableSnapshot {

    public static UserSnapshotDto from(User user) {
        return new UserSnapshotDto(user.getName(), user.getEmail(), user.getRole().name());
    }

    @Override
    public EntityType entityType() { return EntityType.USER; }

    @Override
    public List<ChangeEntry> diff(AuditableSnapshot previous) {
        UserSnapshotDto prev = previous instanceof UserSnapshotDto p ? p : null;
        List<ChangeEntry> changes = new ArrayList<>();
        String prevName  = field(prev, UserSnapshotDto::name);
        String prevEmail = field(prev, UserSnapshotDto::email);
        String prevRole  = field(prev, UserSnapshotDto::role);
        if (!Objects.equals(prevName, name()))
            changes.add(new FieldChange(Fields.name,  prevName,  name()));
        if (!Objects.equals(prevEmail, email()))
            changes.add(new FieldChange(Fields.email, prevEmail, email()));
        if (!Objects.equals(prevRole, role()))
            changes.add(new FieldChange(Fields.role,  prevRole,         role()));
        return changes;
    }

    @Override
    public List<ChangeEntry.FieldChange> allFields() {
        return List.of(
                new FieldChange(Fields.name,  null, name()),
                new FieldChange(Fields.email, null, email()),
                new FieldChange(Fields.role,  null, role()));
    }
}
