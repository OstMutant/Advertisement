package org.ost.attachment.spi;

import java.util.Optional;

/** Implemented by the host application to supply the current user's ID. */
@FunctionalInterface
public interface AttachmentCurrentUserProvider {

    Optional<Long> getCurrentUserId();
}
