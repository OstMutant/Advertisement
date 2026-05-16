package org.ost.advertisement.attachment.spi;

import java.util.Optional;

@FunctionalInterface
public interface AttachmentCurrentUserProvider {
    Optional<Long> getCurrentUserId();
}
