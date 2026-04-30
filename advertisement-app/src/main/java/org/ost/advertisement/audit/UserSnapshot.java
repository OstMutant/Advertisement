package org.ost.advertisement.audit;

import org.ost.advertisement.entities.User;

public record UserSnapshot(
        @AuditedField String name,
        @AuditedField String email,
        @AuditedField String role
) {
    public static UserSnapshot from(User user) {
        return new UserSnapshot(user.getName(), user.getEmail(), user.getRole().name());
    }
}
