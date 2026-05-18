package org.ost.platform.core.spi;

import java.util.Optional;

@FunctionalInterface
public interface CurrentUserProvider {
    Optional<Long> getCurrentUserId();
}
