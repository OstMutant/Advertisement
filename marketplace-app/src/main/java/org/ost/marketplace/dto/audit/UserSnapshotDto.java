package org.ost.marketplace.dto.audit;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.api.AuditedField;
import org.ost.marketplace.entities.User;
import org.ost.platform.core.model.EntityType;

@JsonTypeName("user")
public record UserSnapshotDto(
        @AuditedField String name,
        @AuditedField String email,
        @AuditedField String role
) implements AuditableSnapshot {
    public static UserSnapshotDto from(User user) {
        return new UserSnapshotDto(user.getName(), user.getEmail(), user.getRole().name());
    }

    @Override
    public EntityType entityType() { return EntityType.USER; }
}
