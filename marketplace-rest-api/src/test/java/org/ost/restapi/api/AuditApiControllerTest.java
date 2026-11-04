package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.authenticateAs;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.clearAuthentication;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.mockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fast MockMvc contract tests for {@link AuditApiController} -- mocked orchestrator services, no DB. */
@ExtendWith(MockitoExtension.class)
class AuditApiControllerTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private AuditQueryService auditQueryService;
    @Mock private UserProfileService userProfileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new AuditApiController(auditQueryService, userProfileService));
        authenticateAs(ACTOR_ID);
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    // ── timeline — positive ──────────────────────────────────────────────

    @Test
    void timeline_returnsEntriesWithPagingHeaders() throws Exception {
        AuditTimelineItemDto<AuditableSnapshot> item = new AuditTimelineItemDto<>(1L,
                new EntityRef(EntityType.ADVERTISEMENT, 7L), ActionType.CREATED, Instant.parse("2026-01-01T00:00:00Z"),
                List.of(), ACTOR_ID, null, null);
        when(userProfileService.resolveTimelinePageSize(ACTOR_ID)).thenReturn(10);
        when(auditQueryService.getTimelinePage(eq(ACTOR_ID), any(), any(), eq(0), eq(10))).thenReturn(List.of(item));
        when(auditQueryService.countTimeline(eq(ACTOR_ID), any())).thenReturn(1);

        mockMvc.perform(get("/api/audit/timeline"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].entityType").value("ADVERTISEMENT"))
                .andExpect(jsonPath("$[0].entityId").value(7));
    }

    @Test
    void timeline_defaultsToNewestFirst() throws Exception {
        when(userProfileService.resolveTimelinePageSize(ACTOR_ID)).thenReturn(10);
        when(auditQueryService.getTimelinePage(eq(ACTOR_ID), any(), any(), eq(0), eq(10))).thenReturn(List.of());

        mockMvc.perform(get("/api/audit/timeline")).andExpect(status().isOk());

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(auditQueryService).getTimelinePage(eq(ACTOR_ID), any(), sortCaptor.capture(), eq(0), eq(10));
        assertThat(sortCaptor.getValue()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // ── timeline — negative ──────────────────────────────────────────────

    @Test
    void timeline_notPrivileged_returnsForbidden() throws Exception {
        when(userProfileService.resolveTimelinePageSize(ACTOR_ID)).thenReturn(10);
        when(auditQueryService.getTimelinePage(eq(ACTOR_ID), any(), any(), anyInt(), anyInt()))
                .thenThrow(new AccessDeniedException("not privileged"));

        mockMvc.perform(get("/api/audit/timeline")).andExpect(status().isForbidden());
    }

    @Test
    void timeline_unknownSortField_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/audit/timeline").param("sort", "bogus")).andExpect(status().isBadRequest());
    }
}
