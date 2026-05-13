package org.ost.advertisement.audit;

import java.util.Optional;

public interface AuditUserProvider {
    Optional<Long> getCurrentUserId();
}
