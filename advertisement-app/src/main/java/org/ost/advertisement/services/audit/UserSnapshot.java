package org.ost.advertisement.services.audit;

import org.ost.advertisement.audit.api.AuditableSnapshot;
import org.ost.advertisement.audit.api.AuditedField;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.core.model.EntityType;

public record UserSnapshot(
        @AuditedField String name,
        @AuditedField String email,
        @AuditedField String role
) implements AuditableSnapshot {
    public static UserSnapshot from(User user) {
        return new UserSnapshot(user.getName(), user.getEmail(), user.getRole().name());
    }

    @Override
    public EntityType entityType() { return EntityType.USER; }
}
