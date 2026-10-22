package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: the moderation queue of flagged reviews and replies, approve and confirm-violation decisions, and the access rules around them. */
@Level3ScenarioTest
class ModerationScenarioTest extends AbstractRestApiScenarioTest {

    private static final String TEXT_GREAT = "Great";
    private static final String QUEUE_PATH = "/api/moderation/queue";
    private static final String FLAG_BODY = """
            {"reason":"Spam"}""";
    private static final Pattern REPLY_ID_PATTERN = Pattern.compile("\"reply\":\\{\"id\":(\\d+)");

    @Test
    void flaggedReview_appearsInQueue_andApprovalMakesItVisibleAgain() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long profileId = createProviderProfile(owner);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId, 5, TEXT_GREAT));
        flag("/api/feedback/" + feedbackId + "/flags", stranger);

        mockMvc.perform(get(QUEUE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].type").value("FEEDBACK"))
                .andExpect(jsonPath("$[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$[0].text").value(TEXT_GREAT))
                .andExpect(jsonPath("$[0].reason").value("Spam"));

        decide("/api/moderation/feedback/" + feedbackId + "/approve", admin)
                .andExpect(status().isNoContent());

        mockMvc.perform(get(feedbackPath(profileId)))
                .andExpect(jsonPath("$[0].hidden").value(false))
                .andExpect(jsonPath("$[0].text").value(TEXT_GREAT));

        mockMvc.perform(get(QUEUE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void confirmedViolation_staysHidden_andCannotBeConfirmedAgain() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long profileId = createProviderProfile(owner);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId, 5, TEXT_GREAT));
        flag("/api/feedback/" + feedbackId + "/flags", stranger);
        String confirmPath = "/api/moderation/feedback/" + feedbackId + "/confirm-violation";

        decide(confirmPath, admin).andExpect(status().isNoContent());

        mockMvc.perform(get(feedbackPath(profileId)))
                .andExpect(jsonPath("$[0].hidden").value(true))
                .andExpect(jsonPath("$[0].text").value(nullValue()));

        decide(confirmPath, admin).andExpect(status().isConflict());

        decide("/api/moderation/feedback/" + feedbackId + "/approve", admin).andExpect(status().isNoContent());

        mockMvc.perform(get(feedbackPath(profileId)))
                .andExpect(jsonPath("$[0].hidden").value(false))
                .andExpect(jsonPath("$[0].text").value(TEXT_GREAT));
    }

    @Test
    void approvingAnUnflaggedReview_isConflict() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long profileId = createProviderProfile(owner);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId, 5, TEXT_GREAT));

        decide("/api/moderation/feedback/" + feedbackId + "/approve", admin).andExpect(status().isConflict());
    }

    @Test
    void replyFlaggedAndApproved() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long profileId = createProviderProfile(owner);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId, 5, TEXT_GREAT));
        mockMvc.perform(post("/api/feedback/" + feedbackId + "/reply")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":"Thank you"}"""))
                .andExpect(status().isCreated());
        flag("/api/feedback/" + feedbackId + "/reply/flags", stranger);

        mockMvc.perform(get(QUEUE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].type").value("COMMENT"))
                .andExpect(jsonPath("$[0].status").value("HIDDEN"));

        String publicList = mockMvc.perform(get(feedbackPath(profileId)))
                .andExpect(jsonPath("$[0].reply.hidden").value(true))
                .andReturn().getResponse().getContentAsString();
        long replyId = replyId(publicList);

        decide("/api/moderation/replies/" + replyId + "/approve", admin).andExpect(status().isNoContent());

        mockMvc.perform(get(feedbackPath(profileId)))
                .andExpect(jsonPath("$[0].reply.hidden").value(false))
                .andExpect(jsonPath("$[0].reply.text").value("Thank you"));
    }

    @Test
    void regularUser_cannotReadQueueOrModerate() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long profileId = createProviderProfile(owner);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId, 5, TEXT_GREAT));

        mockMvc.perform(get(QUEUE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(reviewer)))
                .andExpect(status().isForbidden());

        decide("/api/moderation/feedback/" + feedbackId + "/approve", reviewer).andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(QUEUE_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    @Test
    void confirmFeedbackReplyViolation_hidesReply_andCannotBeConfirmedTwice() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        PostedReply reply = flaggedReply(owner, reviewer, stranger);
        long replyId = replyIdOf(reply);
        String confirmPath = "/api/moderation/replies/" + replyId + "/confirm-violation";

        decide(confirmPath, admin).andExpect(status().isNoContent());

        mockMvc.perform(get(feedbackPath(reply.profileId())))
                .andExpect(jsonPath("$[0].reply.hidden").value(true));
        mockMvc.perform(get(QUEUE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(header().string("X-Total-Count", "0"));

        decide(confirmPath, admin).andExpect(status().isConflict());

        decide("/api/moderation/replies/" + replyId + "/approve", admin).andExpect(status().isNoContent());

        mockMvc.perform(get(feedbackPath(reply.profileId())))
                .andExpect(jsonPath("$[0].reply.hidden").value(false))
                .andExpect(jsonPath("$[0].reply.text").value("Thank you"));
    }

    @Test
    void moderator_confirmsFeedbackReplyViolation() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        promoteToModerator(admin, moderator, "Moderator");
        PostedReply reply = flaggedReply(owner, reviewer, stranger);

        decide("/api/moderation/replies/" + replyIdOf(reply) + "/confirm-violation", moderator)
                .andExpect(status().isNoContent());
    }

    @Test
    void plainUser_confirmingFeedbackReplyViolation_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        PostedReply reply = flaggedReply(owner, reviewer, stranger);

        decide("/api/moderation/replies/" + replyIdOf(reply) + "/confirm-violation", stranger)
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_confirmingFeedbackReplyViolation_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        PostedReply reply = flaggedReply(owner, reviewer, stranger);

        mockMvc.perform(post("/api/moderation/replies/" + replyIdOf(reply) + "/confirm-violation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownReply_confirmViolation_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        decide("/api/moderation/replies/999999/confirm-violation", admin).andExpect(status().isNotFound());
    }

    @Test
    void unflaggedReply_confirmViolation_isConflict() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        PostedReply reply = postReply(owner, reviewer);

        decide("/api/moderation/replies/" + replyIdOf(reply) + "/confirm-violation", admin)
                .andExpect(status().isConflict());
    }

    /** Posts an owner reply on a review by the given reviewer and returns where it lives. */
    private PostedReply postReply(RegisteredUser owner, RegisteredUser reviewer) throws Exception {
        long profileId = createProviderProfile(owner);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId, 5, TEXT_GREAT));
        mockMvc.perform(post("/api/feedback/" + feedbackId + "/reply")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":"Thank you"}"""))
                .andExpect(status().isCreated());
        return new PostedReply(profileId, feedbackId);
    }

    /** Posts an owner reply on a review and flags that reply as the given stranger. */
    private PostedReply flaggedReply(RegisteredUser owner, RegisteredUser reviewer, RegisteredUser stranger) throws Exception {
        PostedReply reply = postReply(owner, reviewer);
        flag("/api/feedback/" + reply.feedbackId() + "/reply/flags", stranger);
        return reply;
    }

    /** Reads the id of the reply as the public feedback list shows it. */
    private long replyIdOf(PostedReply reply) throws Exception {
        return replyId(mockMvc.perform(get(feedbackPath(reply.profileId())))
                .andReturn().getResponse().getContentAsString());
    }

    /** Promotes the given user to MODERATOR as the administrator, keeping the name the user registered with. */
    private void promoteToModerator(RegisteredUser admin, RegisteredUser user, String name) throws Exception {
        String userUrl = "/api/users/" + user.id();
        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"%s","role":"MODERATOR"}""".formatted(name)))
                .andExpect(status().isOk());
    }

    /** Reads the resource's current ETag as the viewer, so an update can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    /** Coordinates of a reply posted on a review of a provider profile; the reply id is read back from the public list. */
    private record PostedReply(long profileId, long feedbackId) {
    }

    /** Creates a MASTER provider profile as the given owner and returns its id. */
    private long createProviderProfile(RegisteredUser owner) throws Exception {
        String profileResponse = mockMvc.perform(post("/api/provider-profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"kind":"MASTER","about":"Experienced plumber"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(profileResponse);
    }

    /** Posts one review as the given reviewer, asserts it was created and returns the response body. */
    private String createReview(RegisteredUser reviewer, long profileId, int rating, String text) throws Exception {
        return mockMvc.perform(post(feedbackPath(profileId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"rating":%d,"text":"%s"}""".formatted(rating, text)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
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

    private static long replyId(String publicFeedbackList) {
        Matcher matcher = REPLY_ID_PATTERN.matcher(publicFeedbackList);
        if (!matcher.find()) {
            throw new IllegalStateException("No reply id found in response: " + publicFeedbackList);
        }
        return Long.parseLong(matcher.group(1));
    }

    private static String feedbackPath(long profileId) {
        return "/api/provider-profiles/" + profileId + "/feedback";
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
