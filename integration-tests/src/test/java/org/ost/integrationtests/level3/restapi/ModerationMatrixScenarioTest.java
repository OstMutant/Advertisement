package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 matrix: every moderation REST operation across the anonymous, stranger, reviewer, moderator and administrator roles, with its filter validation and decision edges. */
@Level3ScenarioTest
class ModerationMatrixScenarioTest extends AbstractRestApiScenarioTest {

    private static final String QUEUE = "/api/moderation/queue";
    private static final String UNKNOWN_ID = "999999";
    private static final String PROFILE_BODY = """
            {"kind":"MASTER","about":"Experienced plumber","phone":"+380501234567"}""";
    private static final String REVIEW_BODY = """
            {"rating":5,"text":"Great"}""";
    private static final String REPLY_BODY = """
            {"text":"Thanks"}""";
    private static final String FLAG_BODY = """
            {"reason":"spam"}""";

    // ── queue ────────────────────────────────────────────────────────────

    @Test
    void queue_anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get(QUEUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void queue_admin_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(QUEUE).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void queue_moderator_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        promoteToModerator(admin, moderator);

        mockMvc.perform(get(QUEUE).header(HttpHeaders.AUTHORIZATION, bearer(moderator)))
                .andExpect(status().isOk());
    }

    @Test
    void queue_negativePage_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(QUEUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queue_unknownSortField_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(QUEUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("sort", "bogus,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queue_unknownType_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(QUEUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("types", "FOO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queue_unknownStatus_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(QUEUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("statuses", "FOO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queue_createdAtStartAfterEnd_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(QUEUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("createdAtStart", "2030-01-02T00:00:00Z")
                        .param("createdAtEnd", "2030-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queue_authorNameLongerThan255_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(QUEUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("authorName", "a".repeat(256)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queue_reasonTextLongerThan255_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(QUEUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("reasonText", "a".repeat(256)))
                .andExpect(status().isBadRequest());
    }

    // ── approve review ───────────────────────────────────────────────────

    @Test
    void approveFeedback_anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/moderation/feedback/" + UNKNOWN_ID + "/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approveFeedback_unknownId_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        decide("/api/moderation/feedback/" + UNKNOWN_ID + "/approve", admin)
                .andExpect(status().isNotFound());
    }

    @Test
    void approveFeedback_moderatorApprovesFlaggedReview_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        promoteToModerator(admin, moderator);
        long feedbackId = flaggedReview(owner, reviewer, stranger);

        decide("/api/moderation/feedback/" + feedbackId + "/approve", moderator)
                .andExpect(status().isNoContent());
    }

    @Test
    void approveFeedback_adminApprovesConfirmedViolation_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long feedbackId = flaggedReview(owner, reviewer, stranger);
        decide("/api/moderation/feedback/" + feedbackId + "/confirm-violation", admin)
                .andExpect(status().isNoContent());

        decide("/api/moderation/feedback/" + feedbackId + "/approve", admin)
                .andExpect(status().isNoContent());
    }

    // ── confirm review violation ─────────────────────────────────────────

    @Test
    void confirmFeedbackViolation_anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/moderation/feedback/" + UNKNOWN_ID + "/confirm-violation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmFeedbackViolation_plainUser_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");

        decide("/api/moderation/feedback/" + UNKNOWN_ID + "/confirm-violation", stranger)
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmFeedbackViolation_unknownId_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        decide("/api/moderation/feedback/" + UNKNOWN_ID + "/confirm-violation", admin)
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmFeedbackViolation_moderatorConfirmsFlaggedReview_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        promoteToModerator(admin, moderator);
        long feedbackId = flaggedReview(owner, reviewer, stranger);

        decide("/api/moderation/feedback/" + feedbackId + "/confirm-violation", moderator)
                .andExpect(status().isNoContent());
    }

    // ── approve reply ────────────────────────────────────────────────────

    @Test
    void approveFeedbackReply_anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/moderation/replies/" + UNKNOWN_ID + "/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approveFeedbackReply_plainUser_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");

        decide("/api/moderation/replies/" + UNKNOWN_ID + "/approve", stranger)
                .andExpect(status().isForbidden());
    }

    @Test
    void approveFeedbackReply_unknownId_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        decide("/api/moderation/replies/" + UNKNOWN_ID + "/approve", admin)
                .andExpect(status().isNotFound());
    }

    @Test
    void approveFeedbackReply_unflaggedReply_isConflict() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long feedbackId = createReview(owner, reviewer);
        long replyId = JsonScenarioUtils.extractId(replyOn(owner, feedbackId));

        decide("/api/moderation/replies/" + replyId + "/approve", admin)
                .andExpect(status().isConflict());
    }

    @Test
    void approveFeedbackReply_moderatorApprovesFlaggedReply_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        promoteToModerator(admin, moderator);
        long replyId = flaggedReply(owner, reviewer, stranger);

        decide("/api/moderation/replies/" + replyId + "/approve", moderator)
                .andExpect(status().isNoContent());
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

    /** Posts a valid review on the owner's new profile as the reviewer and returns the review's id. */
    private long createReview(RegisteredUser owner, RegisteredUser reviewer) throws Exception {
        long profileId = createProviderProfile(owner);
        String created = mockMvc.perform(post("/api/provider-profiles/" + profileId + "/feedback")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(created);
    }

    /** Posts a valid owner reply on the given review and returns the reply response body. */
    private String replyOn(RegisteredUser owner, long feedbackId) throws Exception {
        return mockMvc.perform(post("/api/feedback/" + feedbackId + "/reply")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(REPLY_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** Flags a review as the given stranger and returns the review's id. */
    private long flaggedReview(RegisteredUser owner, RegisteredUser reviewer, RegisteredUser stranger) throws Exception {
        long feedbackId = createReview(owner, reviewer);
        flag("/api/feedback/" + feedbackId + "/flags", stranger);
        return feedbackId;
    }

    /** Posts an owner reply on a review, flags that reply as the stranger and returns the reply's id. */
    private long flaggedReply(RegisteredUser owner, RegisteredUser reviewer, RegisteredUser stranger) throws Exception {
        long feedbackId = createReview(owner, reviewer);
        long replyId = JsonScenarioUtils.extractId(replyOn(owner, feedbackId));
        flag("/api/feedback/" + feedbackId + "/reply/flags", stranger);
        return replyId;
    }

    private void flag(String path, RegisteredUser user) throws Exception {
        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isNoContent());
    }

    private ResultActions decide(String path, RegisteredUser moderator) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(moderator)));
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
