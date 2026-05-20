package org.ost.platform.core.spi;

import java.util.Optional;

@FunctionalInterface
public interface CurrentActorProvider {
    Optional<Long> getCurrentActorId();
}
