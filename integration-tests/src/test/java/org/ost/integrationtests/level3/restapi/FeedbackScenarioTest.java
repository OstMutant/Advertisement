package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: provider-profile reviews -- public list and aggregate, one review per user, owner-only replies, author-only edits, flags and the daily review limit. */
@Level3ScenarioTest
class FeedbackScenarioTest extends AbstractRestApiScenarioTest {

    private static final String TEXT_GREAT = "Great";
    private static final String FLAG_BODY = """
            {"reason":"Spam"}""";

    @Test
    void visitor_listsEmpty_thenReviewerReviews_andProfileShowsIt() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(get(feedbackPath(profileId)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$").isEmpty());

        createReview(reviewer, profileId, 5, TEXT_GREAT);

        mockMvc.perform(get(feedbackPath(profileId)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].text").value(TEXT_GREAT))
                .andExpect(jsonPath("$[0].hidden").value(false));

        mockMvc.perform(get(feedbackPath(profileId) + "/aggregate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0));
    }

    @Test
    void secondReviewBySameUser_isConflict() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long profileId = createProviderProfile(owner);
        createReview(reviewer, profileId, 5, TEXT_GREAT);

        mockMvc.perform(reviewRequest(reviewer, profileId, 4, "Again"))
                .andExpect(status().isConflict());
    }

    @Test
    void editOwnReview_withIfMatch() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long profileId = createProviderProfile(owner);
        String created = createReview(reviewer, profileId, 5, TEXT_GREAT);
        String editPath = "/api/feedback/" + JsonScenarioUtils.extractId(created);
        String ifMatch = "\"" + JsonScenarioUtils.extractLongField(created, "version") + "\"";
        String editBody = """
                {"rating":4,"text":"Changed"}""";

        mockMvc.perform(put(editPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .header(HttpHeaders.IF_MATCH, ifMatch)
                        .contentType(MediaType.APPLICATION_JSON).content(editBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Changed"));

        mockMvc.perform(put(editPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .header(HttpHeaders.IF_MATCH, ifMatch)
                        .contentType(MediaType.APPLICATION_JSON).content(editBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(editPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .contentType(MediaType.APPLICATION_JSON).content(editBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reply_onlyByProfileOwner() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long profileId = createProviderProfile(owner);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId, 5, TEXT_GREAT));
        String replyPath = "/api/feedback/" + feedbackId + "/reply";

        mockMvc.perform(post(replyPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":"Not mine"}"""))
                .andExpect(status().isForbidden());

        String reply = mockMvc.perform(post(replyPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":"Thank you"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get(feedbackPath(profileId)))
                .andExpect(jsonPath("$[0].reply.text").value("Thank you"))
                .andExpect(jsonPath("$[0].reply.hidden").value(false));

        mockMvc.perform(put(replyPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, "\"" + JsonScenarioUtils.extractLongField(reply, "version") + "\"")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"text":"Edited reply"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(delete(replyPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(feedbackPath(profileId)))
                .andExpect(jsonPath("$[0].reply").doesNotExist());
    }

    @Test
    void flag_byOtherUserAccepted_andSelfFlagIsForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long profileId = createProviderProfile(owner);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId, 5, TEXT_GREAT));
        String flagPath = "/api/feedback/" + feedbackId + "/flags";

        mockMvc.perform(post(flagPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(flagPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/feedback/999999/flags")
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void afterFlag_entryIsHiddenWithoutText() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long profileId = createProviderProfile(owner);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId, 5, TEXT_GREAT));

        mockMvc.perform(post("/api/feedback/" + feedbackId + "/flags")
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(feedbackPath(profileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hidden").value(true))
                .andExpect(jsonPath("$[0].rating").value(nullValue()))
                .andExpect(jsonPath("$[0].text").value(nullValue()));
    }

    @Test
    void flagTwice_secondIsConflict_andFirstReasonSurvives() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        RegisteredUser secondStranger = registerUserAndIssueApiKey("Second");
        long profileId = createProviderProfile(owner);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId, 5, TEXT_GREAT));
        String flagPath = "/api/feedback/" + feedbackId + "/flags";

        mockMvc.perform(post(flagPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(flagPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondStranger))
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void anonymousWrites_areUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        long profileId = createProviderProfile(owner);
        long feedbackId = JsonScenarioUtils.extractId(createReview(reviewer, profileId, 5, TEXT_GREAT));
        String feedbackItemPath = "/api/feedback/" + feedbackId;

        assertUnauthorized(post(feedbackPath(profileId))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"rating":5,"text":"Anonymous"}"""));
        assertUnauthorized(put(feedbackItemPath)
                .header(HttpHeaders.IF_MATCH, "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"rating":4,"text":"Anonymous"}"""));
        assertUnauthorized(post(feedbackItemPath + "/reply")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"text":"Anonymous"}"""));
        assertUnauthorized(post(feedbackItemPath + "/flags")
                .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY));
        assertUnauthorized(post(feedbackItemPath + "/reply/flags")
                .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY));
    }

    @Test
    void eleventhReviewInADay_isTooManyRequests() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser reviewer = registerUserAndIssueApiKey("Reviewer");
        for (int i = 0; i < 10; i++) {
            RegisteredUser owner = registerUserAndIssueApiKey("Owner");
            createReview(reviewer, createProviderProfile(owner), 5, TEXT_GREAT);
        }
        RegisteredUser eleventhOwner = registerUserAndIssueApiKey("Owner");
        long eleventhProfileId = createProviderProfile(eleventhOwner);

        mockMvc.perform(reviewRequest(reviewer, eleventhProfileId, 5, TEXT_GREAT))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "86400"));
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
        return mockMvc.perform(reviewRequest(reviewer, profileId, rating, text))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder reviewRequest(RegisteredUser reviewer, long profileId, int rating, String text) {
        return post(feedbackPath(profileId))
                .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"rating":%d,"text":"%s"}""".formatted(rating, text));
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    private static String feedbackPath(long profileId) {
        return "/api/provider-profiles/" + profileId + "/feedback";
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
