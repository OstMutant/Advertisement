package org.ost.platform.attachment.spi;

import java.util.Optional;

@FunctionalInterface
public interface AttachmentCurrentUserProvider {
    Optional<Long> getCurrentUserId();
}
