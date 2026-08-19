package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityRef;
import org.springframework.stereotype.Service;

/**
 * Shared {@link AttachmentPort#softDeleteAll} write, reused by every domain's delete-cascade.
 * Not to be confused with {@code attachment-spring-boot-starter}'s own {@code
 * AttachmentCleanupService}, the scheduled sweep for orphaned S3 files.
 */
@Service
@RequiredArgsConstructor
public class AttachmentSoftDeleteService {

    private final ComponentFactory<AttachmentPort> attachmentPortFactory;

    public void softDeleteAll(@NonNull EntityRef entity, @NonNull Long actorId) {
        attachmentPortFactory.ifAvailable(p -> p.softDeleteAll(entity, actorId));
    }
}
