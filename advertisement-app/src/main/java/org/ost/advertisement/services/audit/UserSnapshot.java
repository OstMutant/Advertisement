package org.ost.advertisement.services.audit;

import org.ost.advertisement.audit.AuditableSnapshot;
import org.ost.advertisement.audit.AuditedField;
import org.ost.advertisement.entities.User;

public record UserSnapshot(
        @AuditedField String name,
        @AuditedField String email,
        @AuditedField String role
) implements AuditableSnapshot {
    public static UserSnapshot from(User user) {
        return new UserSnapshot(user.getName(), user.getEmail(), user.getRole().name());
    }

    @Override
    public String entityType() { return "USER"; }
}
