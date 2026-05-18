package org.ost.platform.audit.spi;

import java.util.Map;
import java.util.Set;

public interface AuditActorNameResolver {

    Map<Long, String> resolveNames(Set<Long> actorIds);
}
