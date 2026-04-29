package org.ost.advertisement.events.spi;

import java.util.Optional;

@FunctionalInterface
public interface AttachmentCurrentUserProvider {
    Optional<Long> getCurrentUserId();
}
