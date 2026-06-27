package org.ost.platform.audit.spi;

import lombok.NonNull;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.EntityType;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface AuditPort {

    // ── write side ────────────────────────────────────────────────────────────

    void captureCreation(@NonNull Long entityId, @NonNull AuditableSnapshot snapshot, @NonNull Long actorId);
    void captureUpdate(@NonNull Long entityId, @NonNull AuditableSnapshot before, @NonNull AuditableSnapshot after, @NonNull Long actorId);
    void captureDeletion(@NonNull Long entityId, @NonNull AuditableSnapshot snapshot, @NonNull Long actorId);
    void captureRestore(@NonNull Long entityId, @NonNull AuditableSnapshot snapshot, @NonNull Long actorId);

    <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> getSnapshotContent(@NonNull Long snapshotId, @NonNull EntityType entityType);

    // ── read side (UI) ────────────────────────────────────────────────────────

    List<AuditActivityItemDto<? extends AuditableSnapshot>> getEntityActivity(
            @NonNull EntityType entityType, @NonNull Long entityId,
            @NonNull Long userId, boolean showAll);

    Optional<AuditableSnapshot> getLastSnapshot(@NonNull EntityType entityType, @NonNull Long entityId);

    List<AuditTimelineItemDto<AuditableSnapshot>> getTimelinePage(@NonNull AuditTimelineFilterDto filter, @NonNull Sort sort, int page, int size);

    int countTimeline(@NonNull AuditTimelineFilterDto filter);
}
