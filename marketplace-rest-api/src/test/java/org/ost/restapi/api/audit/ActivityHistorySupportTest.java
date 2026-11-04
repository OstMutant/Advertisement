package org.ost.restapi.api.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.user.dto.UserSnapshotDto;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ActivityHistorySupport} -- mapping of the audit port results, no Spring context. */
@ExtendWith(MockitoExtension.class)
class ActivityHistorySupportTest {

    private static final Long ACTOR = 10L;

    @Mock private AuditQueryService auditQueryService;

    private ActivityHistorySupport support;

    @BeforeEach
    void setUp() {
        support = new ActivityHistorySupport(auditQueryService);
    }

    @Test
    void history_mapsEachEntry() {
        Instant createdAt = Instant.parse("2026-01-02T00:00:00Z");
        ChangeEntry change = new ChangeEntry.FieldChange("title", "A", "B");
        AuditActivityItemDto<AuditableSnapshot> item = new AuditActivityItemDto<>(3L, 2, ActionType.UPDATED, ACTOR,
                createdAt, List.of(change), 2L, null, null);
        when(auditQueryService.getEntityActivity(ACTOR, EntityType.ADVERTISEMENT, 7L)).thenReturn(List.of(item));

        List<ActivityHistorySupport.HistoryEntryResponse> history = support.history(ACTOR, EntityType.ADVERTISEMENT, 7L);

        assertThat(history).hasSize(1);
        ActivityHistorySupport.HistoryEntryResponse entry = history.getFirst();
        assertThat(entry.snapshotId()).isEqualTo(3L);
        assertThat(entry.version()).isEqualTo(2);
        assertThat(entry.actionType()).isEqualTo(ActionType.UPDATED);
        assertThat(entry.actorId()).isEqualTo(ACTOR);
        assertThat(entry.createdAt()).isEqualTo(createdAt);
        assertThat(entry.changes()).containsExactly(change);
    }

    @Test
    void snapshot_found_returnsSnapshotAndVersion() {
        UserSnapshotDto snapshot = new UserSnapshotDto("n", "e@x.com", "USER");
        when(auditQueryService.getSnapshotContent(ACTOR, 7L, 5L, EntityType.USER, UserSnapshotDto.class))
                .thenReturn(Optional.of(new AuditSnapshotContentDto<UserSnapshotDto>(snapshot, 2)));

        ActivityHistorySupport.SnapshotResponse response = support.snapshot(ACTOR, EntityType.USER, 7L, 5L, UserSnapshotDto.class);

        assertThat(response.version()).isEqualTo(2);
        assertThat(response.snapshot()).isEqualTo(snapshot);
    }

    @Test
    void snapshot_missing_throwsNoSuchElement() {
        when(auditQueryService.getSnapshotContent(ACTOR, 7L, 5L, EntityType.USER, UserSnapshotDto.class))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> support.snapshot(ACTOR, EntityType.USER, 7L, 5L, UserSnapshotDto.class))
                .isInstanceOf(NoSuchElementException.class);
    }
}
