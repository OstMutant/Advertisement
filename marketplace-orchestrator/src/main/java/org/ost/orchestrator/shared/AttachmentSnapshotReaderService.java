package org.ost.orchestrator.shared;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;

/** Shared read-only {@link AttachmentPort} snapshot lookup, reused by every domain's save path. */
@Service
@RequiredArgsConstructor
public class AttachmentSnapshotReaderService {

    private final ComponentFactory<AttachmentPort> attachmentPortFactory;

    public Long getLatestSnapshotId(@NonNull EntityType entityType, @NonNull Long entityId) {
        return attachmentPortFactory.findIfAvailable()
                .map(p -> p.getLatestSnapshotId(entityType, entityId))
                .orElse(null);
    }
}
