package org.ost.advertisement.audit.spi;

import java.util.Optional;

public interface AuditUserProvider {
    Optional<Long> getCurrentUserId();
}
