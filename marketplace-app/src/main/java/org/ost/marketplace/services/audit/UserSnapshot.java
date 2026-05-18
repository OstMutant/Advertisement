package org.ost.marketplace.services.audit;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.api.AuditedField;
import org.ost.marketplace.entities.User;
import org.ost.platform.core.model.EntityType;

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
