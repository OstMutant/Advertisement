package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.orchestrator.services.FeedbackCommentWriteService;
import org.ost.orchestrator.services.FeedbackWriteService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.core.EditWindowClosedException;
import org.ost.platform.core.SelfActionNotAllowedException;
import org.ost.platform.core.TooManyAttemptsException;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.feedback.model.FeedbackNotFlaggableException;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.authenticateAs;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.clearAuthentication;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.mockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fast MockMvc contract tests for {@link FeedbackApiController} -- mocked orchestrator services, no DB. */
@ExtendWith(MockitoExtension.class)
class FeedbackApiControllerTest {

    private static final Long AUTHOR_ID = 10L;
    private static final Long OTHER_USER_ID = 11L;
    private static final Long OWNER_ID = 40L;
    private static final Long PROFILE_ID = 7L;
    private static final Long FEEDBACK_ID = 1L;
    private static final Long REPLY_ID = 5L;
    private static final String FLAG_BODY = """
            {"reason":"Spam"}""";
    private static final String REVIEW_BODY = """
            {"rating":5,"text":"Great"}""";

    @Mock private FeedbackAccessService feedbackAccessService;
    @Mock private FeedbackWriteService feedbackWriteService;
    @Mock private FeedbackCommentWriteService feedbackCommentWriteService;
    @Mock private ProviderProfileReadService providerProfileReadService;
    @Mock private UserProfileService userProfileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new FeedbackApiController(feedbackAccessService, feedbackWriteService, feedbackCommentWriteService,
                providerProfileReadService, userProfileService));
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    // ── list & aggregate (public) ────────────────────────────────────────────

    @Test
    void list_returnsEntriesWithPagingHeadersAndReply() throws Exception {
        authenticateAs(AUTHOR_ID);
        stubProfile();
        when(userProfileService.resolveFeedbackPageSize(AUTHOR_ID)).thenReturn(20);
        when(feedbackAccessService.findForEntity(EntityType.PROVIDER_PROFILE, PROFILE_ID, 0, 20)).thenReturn(List.of(feedback(FeedbackModerationStatus.NEW)));
        when(feedbackAccessService.resolveAuthorNames(Set.of(AUTHOR_ID))).thenReturn(Map.of(AUTHOR_ID, "Ivan"));
        when(feedbackAccessService.findCommentByFeedback(FEEDBACK_ID)).thenReturn(Optional.of(reply(FeedbackModerationStatus.NEW)));
        when(feedbackAccessService.getAggregate(EntityType.PROVIDER_PROFILE, PROFILE_ID)).thenReturn(new FeedbackAggregateDto(EntityType.PROVIDER_PROFILE, PROFILE_ID, 5.0, 1));

        mockMvc.perform(get("/api/provider-profiles/7/feedback"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].authorName").value("Ivan"))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].text").value("Great"))
                .andExpect(jsonPath("$[0].hidden").value(false))
                .andExpect(jsonPath("$[0].editable").value(true))
                .andExpect(jsonPath("$[0].reply.text").value("Thanks"))
                .andExpect(jsonPath("$[0].reply.editable").value(false));
    }

    @Test
    void list_hiddenEntry_hasNoRatingOrText() throws Exception {
        authenticateAs(AUTHOR_ID);
        stubProfile();
        when(userProfileService.resolveFeedbackPageSize(AUTHOR_ID)).thenReturn(20);
        when(feedbackAccessService.findForEntity(EntityType.PROVIDER_PROFILE, PROFILE_ID, 0, 20)).thenReturn(List.of(feedback(FeedbackModerationStatus.HIDDEN)));
        when(feedbackAccessService.findCommentByFeedback(FEEDBACK_ID)).thenReturn(Optional.empty());
        when(feedbackAccessService.getAggregate(EntityType.PROVIDER_PROFILE, PROFILE_ID)).thenReturn(new FeedbackAggregateDto(EntityType.PROVIDER_PROFILE, PROFILE_ID, 5.0, 1));

        mockMvc.perform(get("/api/provider-profiles/7/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hidden").value(true))
                .andExpect(jsonPath("$[0].rating").value(nullValue()))
                .andExpect(jsonPath("$[0].text").value(nullValue()));
    }

    @Test
    void list_visitor_usesDefaultPageSize() throws Exception {
        stubProfile();
        when(userProfileService.resolveFeedbackPageSize(null)).thenReturn(20);
        when(feedbackAccessService.findForEntity(EntityType.PROVIDER_PROFILE, PROFILE_ID, 0, 20)).thenReturn(List.of());
        when(feedbackAccessService.getAggregate(EntityType.PROVIDER_PROFILE, PROFILE_ID)).thenReturn(new FeedbackAggregateDto(EntityType.PROVIDER_PROFILE, PROFILE_ID, 0, 0));

        mockMvc.perform(get("/api/provider-profiles/7/feedback"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void list_unknownProfile_returnsNotFound() throws Exception {
        when(providerProfileReadService.findById(PROFILE_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/provider-profiles/7/feedback"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aggregate_returnsAverageAndCount() throws Exception {
        when(feedbackAccessService.getAggregate(EntityType.PROVIDER_PROFILE, PROFILE_ID)).thenReturn(new FeedbackAggregateDto(EntityType.PROVIDER_PROFILE, PROFILE_ID, 4.5, 2));

        mockMvc.perform(get("/api/provider-profiles/7/feedback/aggregate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(2));
    }

    // ── create & edit review ─────────────────────────────────────────────────

    @Test
    void create_returnsCreated() throws Exception {
        authenticateAs(AUTHOR_ID);
        when(feedbackWriteService.create(AUTHOR_ID, PROFILE_ID, 5, "Great")).thenReturn(feedback(FeedbackModerationStatus.NEW));
        when(feedbackAccessService.resolveAuthorNames(Set.of(AUTHOR_ID))).thenReturn(Map.of(AUTHOR_ID, "Ivan"));

        mockMvc.perform(post("/api/provider-profiles/7/feedback")
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.text").value("Great"));
    }

    @Test
    void create_ratingBelowRange_badRequest() throws Exception {
        authenticateAs(AUTHOR_ID);

        mockMvc.perform(post("/api/provider-profiles/7/feedback")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"rating":0,"text":"Great"}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(feedbackWriteService);
    }

    @Test
    void create_ratingAboveRange_badRequest() throws Exception {
        authenticateAs(AUTHOR_ID);

        mockMvc.perform(post("/api/provider-profiles/7/feedback")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"rating":6,"text":"Great"}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(feedbackWriteService);
    }

    @Test
    void create_blankText_badRequest() throws Exception {
        authenticateAs(AUTHOR_ID);

        mockMvc.perform(post("/api/provider-profiles/7/feedback")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"rating":5,"text":"  "}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(feedbackWriteService);
    }

    @Test
    void create_overDailyLimit_tooManyRequestsWithRetryAfter() throws Exception {
        authenticateAs(AUTHOR_ID);
        when(feedbackWriteService.create(AUTHOR_ID, PROFILE_ID, 5, "Great"))
                .thenThrow(new TooManyAttemptsException("Too many feedback entries submitted today, try again tomorrow", Duration.ofDays(1)));

        mockMvc.perform(post("/api/provider-profiles/7/feedback")
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "86400"));
    }

    @Test
    void update_missingIfMatch_badRequest() throws Exception {
        authenticateAs(AUTHOR_ID);

        mockMvc.perform(put("/api/feedback/1")
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW_BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(feedbackWriteService);
    }

    @Test
    void update_passesVersionAndReturnsUpdatedEntry() throws Exception {
        authenticateAs(AUTHOR_ID);
        FeedbackDto updated = new FeedbackDto(FEEDBACK_ID, EntityType.PROVIDER_PROFILE, PROFILE_ID, AUTHOR_ID, 4, "Changed",
                FeedbackModerationStatus.NEW, null, Instant.now(), Instant.now(), 4L);
        when(feedbackWriteService.edit(AUTHOR_ID, FEEDBACK_ID, 4, "Changed", 3L)).thenReturn(updated);
        when(feedbackAccessService.resolveAuthorNames(Set.of(AUTHOR_ID))).thenReturn(Map.of(AUTHOR_ID, "Ivan"));
        stubProfile();
        when(feedbackAccessService.findCommentByFeedback(FEEDBACK_ID)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/feedback/1")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"rating":4,"text":"Changed"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Changed"))
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void update_editWindowClosed_conflict() throws Exception {
        authenticateAs(AUTHOR_ID);
        when(feedbackWriteService.edit(AUTHOR_ID, FEEDBACK_ID, 4, "Changed", 3L))
                .thenThrow(new EditWindowClosedException("Edit window has closed"));

        mockMvc.perform(put("/api/feedback/1")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"rating":4,"text":"Changed"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void update_byAnotherUser_forbidden() throws Exception {
        authenticateAs(OTHER_USER_ID);
        when(feedbackWriteService.edit(OTHER_USER_ID, FEEDBACK_ID, 4, "Changed", 3L))
                .thenThrow(new AccessDeniedException("not the author"));

        mockMvc.perform(put("/api/feedback/1")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"rating":4,"text":"Changed"}"""))
                .andExpect(status().isForbidden());
    }

    // ── reply ────────────────────────────────────────────────────────────────

    @Test
    void reply_create_delegatesAndReturnsCreated() throws Exception {
        authenticateAs(OWNER_ID);
        when(feedbackCommentWriteService.saveComment(new FeedbackCommentSaveDto(null, FEEDBACK_ID, OWNER_ID, "Thanks", null)))
                .thenReturn(reply(FeedbackModerationStatus.NEW));

        mockMvc.perform(post("/api/feedback/1/reply")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":"Thanks"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Thanks"))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    void reply_create_byNonOwner_forbidden() throws Exception {
        authenticateAs(AUTHOR_ID);
        when(feedbackCommentWriteService.saveComment(any())).thenThrow(new AccessDeniedException("not the owner"));

        mockMvc.perform(post("/api/feedback/1/reply")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":"Thanks"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void reply_create_secondReply_conflict() throws Exception {
        authenticateAs(OWNER_ID);
        when(feedbackCommentWriteService.saveComment(any())).thenThrow(new DuplicateKeyException("duplicate reply"));

        mockMvc.perform(post("/api/feedback/1/reply")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":"Thanks"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void reply_update_delegatesWithVersion() throws Exception {
        authenticateAs(OWNER_ID);
        when(feedbackAccessService.findCommentByFeedback(FEEDBACK_ID)).thenReturn(Optional.of(reply(FeedbackModerationStatus.NEW)));
        when(feedbackCommentWriteService.saveComment(new FeedbackCommentSaveDto(REPLY_ID, FEEDBACK_ID, OWNER_ID, "Edited", 2L)))
                .thenReturn(reply(FeedbackModerationStatus.NEW));

        mockMvc.perform(put("/api/feedback/1/reply")
                        .header(HttpHeaders.IF_MATCH, "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":"Edited"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void reply_update_noReply_notFound() throws Exception {
        authenticateAs(OWNER_ID);
        when(feedbackAccessService.findCommentByFeedback(FEEDBACK_ID)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/feedback/1/reply")
                        .header(HttpHeaders.IF_MATCH, "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":"Edited"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void reply_delete_delegates() throws Exception {
        authenticateAs(OWNER_ID);
        when(feedbackAccessService.findCommentByFeedback(FEEDBACK_ID)).thenReturn(Optional.of(reply(FeedbackModerationStatus.NEW)));

        mockMvc.perform(delete("/api/feedback/1/reply"))
                .andExpect(status().isNoContent());
        verify(feedbackCommentWriteService).deleteComment(REPLY_ID, OWNER_ID);
    }

    @Test
    void reply_delete_noReply_notFound() throws Exception {
        authenticateAs(OWNER_ID);
        when(feedbackAccessService.findCommentByFeedback(FEEDBACK_ID)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/feedback/1/reply"))
                .andExpect(status().isNotFound());
    }

    // ── flags ────────────────────────────────────────────────────────────────

    @Test
    void flagFeedback_delegates() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(post("/api/feedback/1/flags")
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isNoContent());
        verify(feedbackAccessService).flagFeedback(FEEDBACK_ID, OTHER_USER_ID, "Spam");
    }

    @Test
    void flagFeedback_ownEntry_forbidden() throws Exception {
        authenticateAs(AUTHOR_ID);
        doThrow(new SelfActionNotAllowedException("Cannot flag your own feedback entry"))
                .when(feedbackAccessService).flagFeedback(FEEDBACK_ID, AUTHOR_ID, "Spam");

        mockMvc.perform(post("/api/feedback/1/flags")
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void flagFeedback_unknownEntry_notFound() throws Exception {
        authenticateAs(OTHER_USER_ID);
        doThrow(new NoSuchElementException("Feedback 1 not found"))
                .when(feedbackAccessService).flagFeedback(FEEDBACK_ID, OTHER_USER_ID, "Spam");

        mockMvc.perform(post("/api/feedback/1/flags")
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void flagFeedback_alreadyHidden_returnsConflict() throws Exception {
        authenticateAs(OTHER_USER_ID);
        doThrow(new FeedbackNotFlaggableException("Feedback 1 is already hidden or reviewed"))
                .when(feedbackAccessService).flagFeedback(FEEDBACK_ID, OTHER_USER_ID, "Spam");

        mockMvc.perform(post("/api/feedback/1/flags")
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void flagReply_delegates() throws Exception {
        authenticateAs(OTHER_USER_ID);
        when(feedbackAccessService.findCommentByFeedback(FEEDBACK_ID)).thenReturn(Optional.of(reply(FeedbackModerationStatus.NEW)));

        mockMvc.perform(post("/api/feedback/1/reply/flags")
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isNoContent());
        verify(feedbackAccessService).flagComment(REPLY_ID, OTHER_USER_ID, "Spam");
    }

    @Test
    void flagReply_noReply_notFound() throws Exception {
        authenticateAs(OTHER_USER_ID);
        when(feedbackAccessService.findCommentByFeedback(FEEDBACK_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/feedback/1/reply/flags")
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void flagFeedbackReply_alreadyHidden_returnsConflict() throws Exception {
        authenticateAs(OTHER_USER_ID);
        when(feedbackAccessService.findCommentByFeedback(FEEDBACK_ID)).thenReturn(Optional.of(reply(FeedbackModerationStatus.NEW)));
        doThrow(new FeedbackNotFlaggableException("Comment 5 is already hidden or reviewed"))
                .when(feedbackAccessService).flagComment(REPLY_ID, OTHER_USER_ID, "Spam");

        mockMvc.perform(post("/api/feedback/1/reply/flags")
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isConflict());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private void stubProfile() {
        when(providerProfileReadService.findById(PROFILE_ID))
                .thenReturn(Optional.of(ProviderProfileDto.builder().id(PROFILE_ID).actorId(OWNER_ID).build()));
    }

    private static FeedbackDto feedback(FeedbackModerationStatus status) {
        return new FeedbackDto(FEEDBACK_ID, EntityType.PROVIDER_PROFILE, PROFILE_ID, AUTHOR_ID, 5, "Great",
                status, null, Instant.now(), null, 3L);
    }

    private static FeedbackCommentDto reply(FeedbackModerationStatus status) {
        return new FeedbackCommentDto(REPLY_ID, FEEDBACK_ID, OWNER_ID, "Thanks", status, null, Instant.now(), null, 2L);
    }
}
