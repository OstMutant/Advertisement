package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.feedback.dto.ModerationQueueFilterDto;
import org.ost.platform.feedback.dto.ModerationQueueItemDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.feedback.model.FeedbackNotModeratableException;
import org.ost.platform.feedback.model.ModerationQueueItemType;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.authenticateAs;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.clearAuthentication;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.mockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fast MockMvc contract tests for {@link ModerationApiController} -- mocked orchestrator services, no DB. */
@ExtendWith(MockitoExtension.class)
class ModerationApiControllerTest {

    private static final Long ACTOR_ID = 1L;
    private static final Long AUTHOR_ID = 10L;
    private static final Long PROFILE_ID = 7L;
    private static final Long FEEDBACK_ID = 1L;
    private static final Long REPLY_ID = 5L;
    private static final int PAGE_SIZE = 15;
    private static final Sort CREATED_AT_DESC = Sort.by(Sort.Direction.DESC, ModerationQueueItemDto.Fields.createdAt);

    @Mock private FeedbackAccessService feedbackAccessService;
    @Mock private UserProfileService userProfileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new ModerationApiController(feedbackAccessService, userProfileService));
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    // ── queue ────────────────────────────────────────────────────────────────

    @Test
    void queue_returnsEntriesWithPagingHeadersAndMappedFields() throws Exception {
        authenticateAs(ACTOR_ID);
        when(userProfileService.resolveModerationPageSize(ACTOR_ID)).thenReturn(PAGE_SIZE);
        when(feedbackAccessService.findModerationQueue(eq(ACTOR_ID), any(ModerationQueueFilterDto.class), eq(CREATED_AT_DESC), eq(0), eq(PAGE_SIZE)))
                .thenReturn(new FeedbackAccessService.ModerationQueuePage(List.of(feedbackItem()), 1));

        mockMvc.perform(get("/api/moderation/queue").param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].type").value("FEEDBACK"))
                .andExpect(jsonPath("$[0].id").value(FEEDBACK_ID))
                .andExpect(jsonPath("$[0].profileId").value(PROFILE_ID))
                .andExpect(jsonPath("$[0].text").value("Great"))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].reason").value("Spam"))
                .andExpect(jsonPath("$[0].authorName").value("Ivan"))
                .andExpect(jsonPath("$[0].status").value("HIDDEN"));
    }

    @Test
    void queue_noSortParam_usesNewestFirstDefault() throws Exception {
        authenticateAs(ACTOR_ID);
        when(userProfileService.resolveModerationPageSize(ACTOR_ID)).thenReturn(PAGE_SIZE);
        when(feedbackAccessService.findModerationQueue(eq(ACTOR_ID), any(ModerationQueueFilterDto.class), eq(CREATED_AT_DESC), eq(0), eq(PAGE_SIZE)))
                .thenReturn(new FeedbackAccessService.ModerationQueuePage(List.of(), 0));

        mockMvc.perform(get("/api/moderation/queue"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void queue_unknownSortField_returnsBadRequest() throws Exception {
        authenticateAs(ACTOR_ID);

        mockMvc.perform(get("/api/moderation/queue").param("sort", "authorName,asc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(feedbackAccessService);
    }

    @Test
    void queue_nonPrivilegedCaller_returnsForbidden() throws Exception {
        authenticateAs(AUTHOR_ID);
        when(userProfileService.resolveModerationPageSize(AUTHOR_ID)).thenReturn(PAGE_SIZE);
        when(feedbackAccessService.findModerationQueue(eq(AUTHOR_ID), any(ModerationQueueFilterDto.class), any(Sort.class), eq(0), eq(PAGE_SIZE)))
                .thenThrow(new AccessDeniedException("not privileged"));

        mockMvc.perform(get("/api/moderation/queue"))
                .andExpect(status().isForbidden());
    }

    // ── decisions ────────────────────────────────────────────────────────────

    @Test
    void approveFeedback_delegatesAndReturnsNoContent() throws Exception {
        authenticateAs(ACTOR_ID);

        mockMvc.perform(post("/api/moderation/feedback/1/approve"))
                .andExpect(status().isNoContent());
        verify(feedbackAccessService).approveFeedback(FEEDBACK_ID, ACTOR_ID);
    }

    @Test
    void confirmFeedbackViolation_delegatesAndReturnsNoContent() throws Exception {
        authenticateAs(ACTOR_ID);

        mockMvc.perform(post("/api/moderation/feedback/1/confirm-violation"))
                .andExpect(status().isNoContent());
        verify(feedbackAccessService).confirmFeedbackViolation(FEEDBACK_ID, ACTOR_ID);
    }

    @Test
    void approveFeedbackReply_delegatesAndReturnsNoContent() throws Exception {
        authenticateAs(ACTOR_ID);

        mockMvc.perform(post("/api/moderation/replies/5/approve"))
                .andExpect(status().isNoContent());
        verify(feedbackAccessService).approveComment(REPLY_ID, ACTOR_ID);
    }

    @Test
    void confirmFeedbackReplyViolation_delegatesAndReturnsNoContent() throws Exception {
        authenticateAs(ACTOR_ID);

        mockMvc.perform(post("/api/moderation/replies/5/confirm-violation"))
                .andExpect(status().isNoContent());
        verify(feedbackAccessService).confirmCommentViolation(REPLY_ID, ACTOR_ID);
    }

    @Test
    void decision_notAwaitingDecision_returnsConflict() throws Exception {
        authenticateAs(ACTOR_ID);
        doThrow(new FeedbackNotModeratableException("Feedback 1 is not awaiting a moderation decision"))
                .when(feedbackAccessService).approveFeedback(FEEDBACK_ID, ACTOR_ID);

        mockMvc.perform(post("/api/moderation/feedback/1/approve"))
                .andExpect(status().isConflict());
    }

    @Test
    void decision_unknownId_returnsNotFound() throws Exception {
        authenticateAs(ACTOR_ID);
        doThrow(new NoSuchElementException("Comment 5 not found"))
                .when(feedbackAccessService).approveComment(REPLY_ID, ACTOR_ID);

        mockMvc.perform(post("/api/moderation/replies/5/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    void decision_nonPrivilegedCaller_returnsForbidden() throws Exception {
        authenticateAs(AUTHOR_ID);
        doThrow(new AccessDeniedException("not privileged"))
                .when(feedbackAccessService).confirmFeedbackViolation(FEEDBACK_ID, AUTHOR_ID);

        mockMvc.perform(post("/api/moderation/feedback/1/confirm-violation"))
                .andExpect(status().isForbidden());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static ModerationQueueItemDto feedbackItem() {
        return ModerationQueueItemDto.builder()
                .type(ModerationQueueItemType.FEEDBACK)
                .id(FEEDBACK_ID)
                .entityId(PROFILE_ID)
                .contentText("Great")
                .rating(5)
                .moderationReason("Spam")
                .authorId(AUTHOR_ID)
                .authorName("Ivan")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .moderationStatus(FeedbackModerationStatus.HIDDEN)
                .build();
    }
}
