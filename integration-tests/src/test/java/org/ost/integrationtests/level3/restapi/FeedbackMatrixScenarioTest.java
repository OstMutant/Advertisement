package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 matrix: every feedback REST operation across the anonymous, owner, reviewer, moderator, stranger and administrator roles, with its validation and precondition edges. */
@Level3ScenarioTest
class FeedbackMatrixScenarioTest extends AbstractRestApiScenarioTest {

    private static final String UNKNOWN_ID = "999999";
    private static final String PROFILE_BODY = """
            {"kind":"MASTER","about":"Experienced plumber","phone":"+380501234567"}""";
    private static final String REVIEW_BODY = """
            {"rating":5,"text":"Great"}""";
    private static final String EDITED_REVIEW_BODY = """
            {"rating":4,"text":"Changed"}""";
    private static final String REPLY_BODY = """
            {"text":"Thanks"}""";
    private static final String EDITED_REPLY_BODY = """
            {"text":"Edited"}""";
    private static final String FLAG_BODY = """
            {"reason":"spam"}""";
    private static final String STALE_IF_MATCH = "\"0\"";

    // ── list ─────────────────────────────────────────────────────────────

    @Test
    void list_unknownProfile_isNotFound() throws Exception {
        mockMvc.perform(get(feedbackPath(999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFeedback_negativePage_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(get(feedbackPath(profileId)).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    // ── aggregate ────────────────────────────────────────────────────────

    @Test
    void aggregate_unknownProfile_isOkWithZeroReviews() throws Exception {
        mockMvc.perform(get(feedbackPath(999999L) + "/aggregate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(0));
    }

    // ── create ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onCreate_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(post(feedbackPath(profileId))
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_unknownProfile_isNotFound() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");

        mockMvc.perform(reviewRequest(reviewer, 999999L, REVIEW_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_missingRating_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(reviewRequest(reviewer, profileId, """
                        {"text":"Great"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_ratingZero_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(reviewRequest(reviewer, profileId, """
                        {"rating":0,"text":"Great"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_ratingSix_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(reviewRequest(reviewer, profileId, """
                        {"rating":6,"text":"Great"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankText_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(reviewRequest(reviewer, profileId, """
                        {"rating":5,"text":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_textLongerThan20000_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(reviewRequest(reviewer, profileId, """
                        {"rating":5,"text":"%s"}""".formatted("a".repeat(20001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_createsReview_isCreated() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(reviewRequest(admin, profileId, REVIEW_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void moderator_createsReview_isCreated() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProviderProfile(owner);
        promoteToModerator(admin, moderator);

        mockMvc.perform(reviewRequest(moderator, profileId, REVIEW_BODY))
                .andExpect(status().isCreated());
    }

    // ── update review ────────────────────────────────────────────────────

    @Test
    void anonymous_onUpdateReview_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        String created = createReview(reviewer, createProviderProfile(owner));

        mockMvc.perform(put(feedbackItemPath(created))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(created))
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REVIEW_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateReview_unknownId_isNotFound() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");

        mockMvc.perform(put("/api/feedback/" + UNKNOWN_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH)
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REVIEW_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_editsOthersReview_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        String created = createReview(reviewer, createProviderProfile(owner));

        mockMvc.perform(put(feedbackItemPath(created))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(created))
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REVIEW_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderator_editsOthersReview_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        promoteToModerator(admin, moderator);
        String created = createReview(reviewer, createProviderProfile(owner));

        mockMvc.perform(put(feedbackItemPath(created))
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(created))
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REVIEW_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateReview_ratingZero_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        String created = createReview(reviewer, createProviderProfile(owner));

        mockMvc.perform(put(feedbackItemPath(created))
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(created))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"rating":0,"text":"Changed"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReview_blankText_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        String created = createReview(reviewer, createProviderProfile(owner));

        mockMvc.perform(put(feedbackItemPath(created))
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(created))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"rating":4,"text":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReview_textLongerThan20000_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        String created = createReview(reviewer, createProviderProfile(owner));

        mockMvc.perform(put(feedbackItemPath(created))
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(created))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"rating":4,"text":"%s"}""".formatted("a".repeat(20001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReview_staleIfMatchAfterEdit_isPreconditionFailed() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        String created = createReview(reviewer, createProviderProfile(owner));

        mockMvc.perform(put(feedbackItemPath(created))
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(created))
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REVIEW_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(put(feedbackItemPath(created))
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH)
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REVIEW_BODY))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void updateReview_missingIfMatch_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        String created = createReview(reviewer, createProviderProfile(owner));

        mockMvc.perform(put(feedbackItemPath(created))
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REVIEW_BODY))
                .andExpect(status().isBadRequest());
    }

    // ── create reply ─────────────────────────────────────────────────────

    @Test
    void anonymous_onCreateReply_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(post(replyPath(feedbackId))
                        .contentType(MediaType.APPLICATION_JSON).content(REPLY_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reviewer_createsReply_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(replyRequest(reviewer, feedbackId, REPLY_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_createsReply_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(replyRequest(admin, feedbackId, REPLY_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReply_unknownFeedback_isNotFound() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(replyRequest(owner, 999999L, REPLY_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReply_blankText_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(replyRequest(owner, feedbackId, """
                        {"text":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReply_textLongerThan20000_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(replyRequest(owner, feedbackId, """
                        {"text":"%s"}""".formatted("a".repeat(20001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void owner_createsReply_isCreated() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(replyRequest(owner, feedbackId, REPLY_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void owner_secondReplyToSameReview_isConflict() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);

        mockMvc.perform(replyRequest(owner, feedbackId, REPLY_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void owner_thirtyFirstReplyInADay_isTooManyRequests() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProviderProfile(owner);
        for (int i = 0; i < 30; i++) {
            RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
            long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId));
            createReply(owner, feedbackId);
        }
        RegisteredUser lastReviewer = registerUserAndIssueApiKey("Reviewer");
        long lastFeedbackId = JsonScenarioUtils.extractId(createReview(lastReviewer, profileId));

        mockMvc.perform(replyRequest(owner, lastFeedbackId, REPLY_BODY))
                .andExpect(status().isTooManyRequests());
    }

    // ── update reply ─────────────────────────────────────────────────────

    @Test
    void anonymous_onUpdateReply_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        String reply = createReply(owner, feedbackId);

        mockMvc.perform(put(replyPath(feedbackId))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(reply))
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REPLY_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateReply_withoutReply_isNotFound() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(put(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH)
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REPLY_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void reviewer_editsReply_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        String reply = createReply(owner, feedbackId);

        mockMvc.perform(put(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(reply))
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REPLY_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_editsReply_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        String reply = createReply(owner, feedbackId);

        mockMvc.perform(put(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(reply))
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REPLY_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateReply_blankText_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        String reply = createReply(owner, feedbackId);

        mockMvc.perform(put(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(reply))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReply_textLongerThan20000_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        String reply = createReply(owner, feedbackId);

        mockMvc.perform(put(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(reply))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":"%s"}""".formatted("a".repeat(20001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReply_staleIfMatchAfterEdit_isPreconditionFailed() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        String reply = createReply(owner, feedbackId);

        mockMvc.perform(put(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, ifMatchOf(reply))
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REPLY_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(put(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH)
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REPLY_BODY))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void updateReply_missingIfMatch_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);

        mockMvc.perform(put(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(EDITED_REPLY_BODY))
                .andExpect(status().isBadRequest());
    }

    // ── delete reply ─────────────────────────────────────────────────────

    @Test
    void anonymous_onDeleteReply_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);

        mockMvc.perform(delete(replyPath(feedbackId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteReply_withoutReply_isNotFound() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(delete(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reviewer_deletesReply_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);

        mockMvc.perform(delete(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_deletesReply_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);

        mockMvc.perform(delete(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_deletesReply_isNoContent() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);

        mockMvc.perform(delete(replyPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
    }

    // ── flag review ──────────────────────────────────────────────────────

    @Test
    void anonymous_onFlagFeedback_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(post(flagPath(feedbackId))
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void flagFeedback_blankReason_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(flagRequest(stranger, flagPath(feedbackId), """
                        {"reason":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void flagFeedback_reasonLongerThan2000_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(flagRequest(stranger, flagPath(feedbackId), """
                        {"reason":"%s"}""".formatted("r".repeat(2001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void flagFeedback_noBody_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(post(flagPath(feedbackId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_flagsReview_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(flagRequest(admin, flagPath(feedbackId), FLAG_BODY))
                .andExpect(status().isNoContent());
    }

    @Test
    void moderator_flagsReview_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        promoteToModerator(admin, moderator);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(flagRequest(moderator, flagPath(feedbackId), FLAG_BODY))
                .andExpect(status().isNoContent());
    }

    // ── flag reply ───────────────────────────────────────────────────────

    @Test
    void anonymous_onFlagReply_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);

        mockMvc.perform(post(replyFlagPath(feedbackId))
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void flagReply_withoutReply_isNotFound() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));

        mockMvc.perform(flagRequest(reviewer, replyFlagPath(feedbackId), FLAG_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void owner_flagsOwnReply_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);

        mockMvc.perform(flagRequest(owner, replyFlagPath(feedbackId), FLAG_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewer_flagsReply_isNoContent() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);

        mockMvc.perform(flagRequest(reviewer, replyFlagPath(feedbackId), FLAG_BODY))
                .andExpect(status().isNoContent());
    }

    @Test
    void flagReply_secondFlagOnSameReply_isConflict() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);
        mockMvc.perform(flagRequest(reviewer, replyFlagPath(feedbackId), FLAG_BODY))
                .andExpect(status().isNoContent());

        mockMvc.perform(flagRequest(stranger, replyFlagPath(feedbackId), FLAG_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void flagReply_blankReason_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);

        mockMvc.perform(flagRequest(reviewer, replyFlagPath(feedbackId), """
                        {"reason":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void flagReply_reasonLongerThan2000_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, createProviderProfile(owner)));
        createReply(owner, feedbackId);

        mockMvc.perform(flagRequest(reviewer, replyFlagPath(feedbackId), """
                        {"reason":"%s"}""".formatted("r".repeat(2001))))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Registers a user and promotes it to MODERATOR as the given administrator. */
    private void promoteToModerator(RegisteredUser admin, RegisteredUser moderator) throws Exception {
        String userUrl = "/api/users/" + moderator.id();
        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Moderator","role":"MODERATOR"}"""))
                .andExpect(status().isOk());
    }

    /** Reads the resource's current ETag as the viewer, so an update can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    /** Creates a MASTER provider profile as the given owner and returns its id. */
    private long createProviderProfile(RegisteredUser owner) throws Exception {
        String profileResponse = mockMvc.perform(post("/api/provider-profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(profileResponse);
    }

    /** Posts a valid review as the given reviewer, asserts it was created and returns the response body. */
    private String createReview(RegisteredUser reviewer, long profileId) throws Exception {
        return mockMvc.perform(reviewRequest(reviewer, profileId, REVIEW_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** Posts a valid reply as the owner, asserts it was created and returns the response body. */
    private String createReply(RegisteredUser owner, long feedbackId) throws Exception {
        return mockMvc.perform(replyRequest(owner, feedbackId, REPLY_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder reviewRequest(RegisteredUser reviewer, long profileId, String body) {
        return post(feedbackPath(profileId))
                .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder replyRequest(RegisteredUser user, long feedbackId, String body) {
        return post(replyPath(feedbackId))
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder flagRequest(RegisteredUser user, String path, String body) {
        return post(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Builds the If-Match value from the version field of a feedback or reply response body. */
    private static String ifMatchOf(String json) {
        return "\"" + JsonScenarioUtils.extractLongField(json, "version") + "\"";
    }

    private static String feedbackItemPath(String createdReviewJson) {
        return "/api/feedback/" + JsonScenarioUtils.extractId(createdReviewJson);
    }

    private static String feedbackPath(long profileId) {
        return "/api/provider-profiles/" + profileId + "/feedback";
    }

    private static String replyPath(long feedbackId) {
        return "/api/feedback/" + feedbackId + "/reply";
    }

    private static String flagPath(long feedbackId) {
        return "/api/feedback/" + feedbackId + "/flags";
    }

    private static String replyFlagPath(long feedbackId) {
        return "/api/feedback/" + feedbackId + "/reply/flags";
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
