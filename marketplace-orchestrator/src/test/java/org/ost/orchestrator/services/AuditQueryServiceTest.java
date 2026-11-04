package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link AuditQueryService}'s privilege and ownership gating over {@link AuditPort}. */
@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    @Mock private ComponentFactory<AuditPort> auditPortFactory;
    @Mock private AuditPort auditPort;
    @Mock private AuthorizationService authorizationService;

    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        service = new AuditQueryService(auditPortFactory, List.of(), authorizationService);
        lenient().when(auditPortFactory.findIfAvailable()).thenReturn(Optional.of(auditPort));
    }

    @Test
    void getEntityActivity_privilegedUser_requestsAllEntries() {
        when(authorizationService.isPrivileged(1L)).thenReturn(true);

        service.getEntityActivity(1L, EntityType.ADVERTISEMENT, 7L);

        verify(auditPort).getEntityActivity(EntityType.ADVERTISEMENT, 7L, 1L, true);
    }

    @Test
    void getEntityActivity_regularUser_requestsOwnEntriesOnly() {
        when(authorizationService.isPrivileged(1L)).thenReturn(false);

        service.getEntityActivity(1L, EntityType.ADVERTISEMENT, 7L);

        verify(auditPort).getEntityActivity(EntityType.ADVERTISEMENT, 7L, 1L, false);
    }

    @Test
    void getTimelinePage_regularUser_isDenied() {
        doThrow(new AccessDeniedException("no")).when(authorizationService).requireIsPrivileged(2L);

        assertThatThrownBy(() -> service.getTimelinePage(2L, AuditTimelineFilterDto.empty(), Sort.unsorted(), 0, 10))
                .isInstanceOf(AccessDeniedException.class);
        verify(auditPort, never()).getTimelinePage(any(), any(), anyInt(), anyInt());
    }

    @Test
    void countTimeline_regularUser_isDenied() {
        doThrow(new AccessDeniedException("no")).when(authorizationService).requireIsPrivileged(2L);

        assertThatThrownBy(() -> service.countTimeline(2L, AuditTimelineFilterDto.empty()))
                .isInstanceOf(AccessDeniedException.class);
        verify(auditPort, never()).countTimeline(any());
    }

    @Test
    void getSnapshotContent_privilegedUser_readsAnySnapshot() {
        UserSnapshotDto snapshot = new UserSnapshotDto("name", "user@example.com", "USER");
        when(authorizationService.isPrivileged(1L)).thenReturn(true);
        when(auditPort.getSnapshotContent(5L, EntityType.USER, UserSnapshotDto.class))
                .thenReturn(Optional.of(new AuditSnapshotContentDto<>(snapshot, 1)));

        Optional<AuditSnapshotContentDto<UserSnapshotDto>> result =
                service.getSnapshotContent(1L, 7L, 5L, EntityType.USER, UserSnapshotDto.class);

        assertThat(result).isPresent();
        verify(auditPort, never()).getEntityActivity(any(), any(), any(), anyBoolean());
    }

    @Test
    void getSnapshotContent_regularUserOwnEntry_isReadable() {
        UserSnapshotDto snapshot = new UserSnapshotDto("name", "user@example.com", "USER");
        when(authorizationService.isPrivileged(2L)).thenReturn(false);
        when(auditPort.getEntityActivity(EntityType.USER, 7L, 2L, false))
                .thenReturn(List.<AuditActivityItemDto<? extends AuditableSnapshot>>of(activityItem(5L, snapshot)));
        when(auditPort.getSnapshotContent(5L, EntityType.USER, UserSnapshotDto.class))
                .thenReturn(Optional.of(new AuditSnapshotContentDto<>(snapshot, 1)));

        Optional<AuditSnapshotContentDto<UserSnapshotDto>> result =
                service.getSnapshotContent(2L, 7L, 5L, EntityType.USER, UserSnapshotDto.class);

        assertThat(result).isPresent();
    }

    @Test
    void getSnapshotContent_regularUserForeignEntry_isDenied() {
        UserSnapshotDto snapshot = new UserSnapshotDto("name", "user@example.com", "USER");
        when(authorizationService.isPrivileged(2L)).thenReturn(false);
        when(auditPort.getEntityActivity(EntityType.USER, 7L, 2L, false))
                .thenReturn(List.<AuditActivityItemDto<? extends AuditableSnapshot>>of(activityItem(6L, snapshot)));

        assertThatThrownBy(() -> service.getSnapshotContent(2L, 7L, 5L, EntityType.USER, UserSnapshotDto.class))
                .isInstanceOf(AccessDeniedException.class);
        verify(auditPort, never()).getSnapshotContent(any(), any(), any());
    }

    private static AuditActivityItemDto<UserSnapshotDto> activityItem(Long snapshotId, UserSnapshotDto snapshot) {
        return new AuditActivityItemDto<>(snapshotId, 1, ActionType.UPDATED, 2L, Instant.now(), List.of(), null, snapshot, null);
    }
}
