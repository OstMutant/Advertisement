package org.ost.advertisement.core.spi;

import java.util.Map;
import java.util.Set;

public interface AuditActorNameResolver {

    Map<Long, String> resolveNames(Set<Long> actorIds);
}
