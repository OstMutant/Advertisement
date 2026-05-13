package org.ost.advertisement.services.audit;

import org.ost.advertisement.audit.AuditableSnapshot;
import org.ost.advertisement.audit.AuditedField;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.events.model.EntityType;

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
