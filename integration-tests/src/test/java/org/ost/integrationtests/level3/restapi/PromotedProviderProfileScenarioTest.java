package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3: promoted provider profiles inside the ordinary list: the badge fields, the default order, the filter and the sort, the reaction to review changes and deletion, and the unchanged total. */
@Level3ScenarioTest
class PromotedProviderProfileScenarioTest extends AbstractRestApiScenarioTest {

    private static final String PROFILES = "/api/provider-profiles";
    private static final String PROFILE_BODY = """
            {"kind":"MASTER","about":"Experienced plumber","phone":"+380501234567"}""";
    private static final String REVIEW_BODY = """
            {"rating":5,"text":"Great"}""";
    private static final String FLAG_BODY = """
            {"reason":"spam"}""";

    @Test
    void list_nobodyPromoted_marksNoProfile() throws Exception {
        registerUserAndIssueApiKey("Admin");
        profileWithReviews("Plain", 0);

        mockMvc.perform(get(PROFILES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].promoted").value(false));
    }

    @Test
    void list_fourReviews_isNotPromoted() throws Exception {
        registerUserAndIssueApiKey("Admin");
        profileWithReviews("Four", 4);

        mockMvc.perform(get(PROFILES))
                .andExpect(jsonPath("$[0].promoted").value(false));
        mockMvc.perform(get(PROFILES).param("promoted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_fifthReview_putsThePromotedProfileFirstAndMarksIt() throws Exception {
        registerUserAndIssueApiKey("Admin");
        ProfileWithReviews promoted = profileWithReviews("Promoted", 5);
        ProfileWithReviews newer = profileWithReviews("Newer", 0);

        mockMvc.perform(get(PROFILES))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].id").value(promoted.profileId()))
                .andExpect(jsonPath("$[0].promoted").value(true))
                .andExpect(jsonPath("$[0].promotionReason").value("TOP_RATED"))
                .andExpect(jsonPath("$[1].id").value(newer.profileId()))
                .andExpect(jsonPath("$[1].promoted").value(false));
    }

    @Test
    void list_promotedFilter_keepsOnlyThePromotedAndTheirCount() throws Exception {
        registerUserAndIssueApiKey("Admin");
        ProfileWithReviews promoted = profileWithReviews("Promoted", 5);
        profileWithReviews("Newer", 0);

        mockMvc.perform(get(PROFILES).param("promoted", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(promoted.profileId()));
    }

    @Test
    void list_promotedSortAscending_listsThePromotedLast() throws Exception {
        registerUserAndIssueApiKey("Admin");
        ProfileWithReviews promoted = profileWithReviews("Promoted", 5);
        ProfileWithReviews newer = profileWithReviews("Newer", 0);

        mockMvc.perform(get(PROFILES).param("sort", "promoted,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newer.profileId()))
                .andExpect(jsonPath("$[1].id").value(promoted.profileId()));
    }

    @Test
    void list_sortWithoutPromoted_mergesThePromotedIntoTheOrdinaryOrder() throws Exception {
        registerUserAndIssueApiKey("Admin");
        ProfileWithReviews promoted = profileWithReviews("Promoted", 5);
        ProfileWithReviews newer = profileWithReviews("Newer", 0);

        mockMvc.perform(get(PROFILES).param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newer.profileId()))
                .andExpect(jsonPath("$[1].id").value(promoted.profileId()))
                .andExpect(jsonPath("$[1].promoted").value(true));
    }

    @Test
    void list_reviewReportedAsViolation_dropsThePromotionAtOnce() throws Exception {
        registerUserAndIssueApiKey("Admin");
        ProfileWithReviews promoted = profileWithReviews("Promoted", 5);

        mockMvc.perform(post("/api/feedback/" + promoted.firstReviewId() + "/flags")
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoted.owner()))
                        .contentType(MediaType.APPLICATION_JSON).content(FLAG_BODY))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PROFILES))
                .andExpect(jsonPath("$[0].promoted").value(false));
    }

    @Test
    void list_profileDeleted_removesItAtOnce() throws Exception {
        registerUserAndIssueApiKey("Admin");
        ProfileWithReviews promoted = profileWithReviews("Promoted", 5);
        String profileUrl = PROFILES + "/" + promoted.profileId();
        String etag = mockMvc.perform(get(profileUrl)).andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(delete(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(promoted.owner()))
                        .header(HttpHeaders.IF_MATCH, etag))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PROFILES).param("promoted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void get_promotedProfile_carriesThePromotionFields() throws Exception {
        registerUserAndIssueApiKey("Admin");
        ProfileWithReviews promoted = profileWithReviews("Promoted", 5);

        mockMvc.perform(get(PROFILES + "/" + promoted.profileId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promoted").value(true))
                .andExpect(jsonPath("$.promotionReason").value("TOP_RATED"));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** A created profile with its owner and the id of its first review. */
    private record ProfileWithReviews(RegisteredUser owner, long profileId, long firstReviewId) {
    }

    /** Registers an owner with one profile and has the given number of other users each leave a 5-star review. */
    private ProfileWithReviews profileWithReviews(String ownerPrefix, int reviews) throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey(ownerPrefix);
        String profile = mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long profileId = JsonScenarioUtils.extractId(profile);
        long firstReviewId = 0;
        for (int i = 0; i < reviews; i++) {
            RegisteredUser reviewer = registerUserAndIssueApiKey(ownerPrefix + "Reviewer" + i);
            String review = mockMvc.perform(post(PROFILES + "/" + profileId + "/feedback")
                            .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                            .contentType(MediaType.APPLICATION_JSON).content(REVIEW_BODY))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            if (i == 0) {
                firstReviewId = JsonScenarioUtils.extractId(review);
            }
        }
        return new ProfileWithReviews(owner, profileId, firstReviewId);
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
