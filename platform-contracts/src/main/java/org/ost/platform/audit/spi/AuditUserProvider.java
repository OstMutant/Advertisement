package org.ost.platform.audit.spi;

import java.util.Optional;

public interface AuditUserProvider {
    Optional<Long> getCurrentUserId();
}
