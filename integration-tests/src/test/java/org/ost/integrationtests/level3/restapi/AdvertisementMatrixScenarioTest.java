package org.ost.integrationtests.level3.restapi;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 matrix: every advertisement REST operation across the anonymous, owner, stranger, moderator and administrator roles, with its validation and precondition edges. */
@Level3ScenarioTest
class AdvertisementMatrixScenarioTest extends AbstractRestApiScenarioTest {

    private static final String ADS = "/api/advertisements";
    private static final String UNKNOWN_ID = "999999";
    private static final String UNKNOWN_ADS = ADS + "/" + UNKNOWN_ID;
    private static final String VALID_BODY = """
            {"title":"Plumbing","description":"Fast","adKind":"OFFER"}""";
    private static final String UPDATED_BODY = """
            {"title":"Plumbing","description":"Fast and reliable","adKind":"OFFER"}""";
    private static final String STALE_IF_MATCH = "\"0\"";

    // ── list ─────────────────────────────────────────────────────────────

    @Test
    void list_sortByUnknownField_isBadRequest() throws Exception {
        mockMvc.perform(get(ADS).param("sort", "bogus,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_unknownAdKind_isBadRequest() throws Exception {
        mockMvc.perform(get(ADS).param("adKinds", "FOO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_titleLongerThan255_isBadRequest() throws Exception {
        mockMvc.perform(get(ADS).param("title", "a".repeat(256)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_createdAtStartAfterEnd_isBadRequest() throws Exception {
        mockMvc.perform(get(ADS)
                        .param("createdAtStart", "2030-01-02T00:00:00Z")
                        .param("createdAtEnd", "2030-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_negativePage_isBadRequest() throws Exception {
        mockMvc.perform(get(ADS).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    // ── create ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onCreate_isUnauthorized() throws Exception {
        mockMvc.perform(post(ADS).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_blankTitle_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","description":"Fast","adKind":"OFFER"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_titleLongerThan255_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"Fast","adKind":"OFFER"}""".formatted("a".repeat(256))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankDescription_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plumbing","description":"","adKind":"OFFER"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingAdKind_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plumbing","description":"Fast"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_unknownAdKind_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plumbing","description":"Fast","adKind":"FOO"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_moreThanTenCategories_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plumbing","description":"Fast","adKind":"OFFER","categoryIds":[1,2,3,4,5,6,7,8,9,10,11]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_createsAd_isCreated() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(post(ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    // ── get ──────────────────────────────────────────────────────────────

    @Test
    void unknownAd_get_isNotFound() throws Exception {
        mockMvc.perform(get(UNKNOWN_ADS))
                .andExpect(status().isNotFound());
    }

    // ── history ──────────────────────────────────────────────────────────

    @Test
    void owner_seesHistory_isOk() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "History");

        assertThat(historyEntries(owner, adUrl + "/history")).isNotEmpty();
    }

    @Test
    void stranger_seesEmptyHistory_isOk() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        String adUrl = ADS + "/" + createAd(owner, "History");

        mockMvc.perform(get(adUrl + "/history").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void admin_seesHistory_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "History");

        assertThat(historyEntries(admin, adUrl + "/history")).isNotEmpty();
    }

    @Test
    void moderator_seesHistory_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "History");

        assertThat(historyEntries(moderator, adUrl + "/history")).isNotEmpty();
    }

    @Test
    void admin_onUnknownAdHistory_isOkWithEmptyList() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(UNKNOWN_ADS + "/history").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── historySnapshot ──────────────────────────────────────────────────

    @Test
    void anonymous_onHistorySnapshot_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Snapshot");
        long oldest = oldestSnapshotId(owner, adUrl);

        mockMvc.perform(get(adUrl + "/history/" + oldest))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_readsSnapshot_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Snapshot");
        long oldest = oldestSnapshotId(owner, adUrl);

        mockMvc.perform(get(adUrl + "/history/" + oldest).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void moderator_readsSnapshot_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Snapshot");
        long oldest = oldestSnapshotId(owner, adUrl);

        mockMvc.perform(get(adUrl + "/history/" + oldest).header(HttpHeaders.AUTHORIZATION, bearer(moderator)))
                .andExpect(status().isOk());
    }

    @Test
    void admin_onUnknownSnapshot_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Snapshot");

        mockMvc.perform(get(adUrl + "/history/" + UNKNOWN_ID).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    // ── update ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onUpdate_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Update");

        mockMvc.perform(put(adUrl)
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_updatesAd_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Update");

        mockMvc.perform(put(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void moderator_updatesAd_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Update");

        mockMvc.perform(put(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void update_blankTitle_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Update");

        mockMvc.perform(put(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","description":"Fast","adKind":"OFFER"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_titleLongerThan255_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Update");

        mockMvc.perform(put(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"Fast","adKind":"OFFER"}""".formatted("a".repeat(256))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_missingAdKind_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Update");

        mockMvc.perform(put(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plumbing","description":"Fast"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAd_onUpdate_isPreconditionFailed() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(put(UNKNOWN_ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH)
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void update_missingIfMatch_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Update");

        // missing If-Match must be a bad request
        mockMvc.perform(put(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isBadRequest());
    }

    // ── restore ──────────────────────────────────────────────────────────

    @Test
    void anonymous_onRestore_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = updatedAd(owner);
        long oldest = oldestSnapshotId(owner, adUrl);

        mockMvc.perform(post(adUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_restoresSnapshot_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = updatedAd(owner);
        long oldest = oldestSnapshotId(owner, adUrl);

        mockMvc.perform(post(adUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl)))
                .andExpect(status().isOk());
    }

    @Test
    void moderator_restoresSnapshot_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = updatedAd(owner);
        long oldest = oldestSnapshotId(owner, adUrl);

        mockMvc.perform(post(adUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl)))
                .andExpect(status().isOk());
    }

    @Test
    void restore_missingIfMatch_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = updatedAd(owner);
        long oldest = oldestSnapshotId(owner, adUrl);

        mockMvc.perform(post(adUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_onUnknownSnapshotRestore_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = updatedAd(owner);

        mockMvc.perform(post(adUrl + "/history/" + UNKNOWN_ID + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl)))
                .andExpect(status().isNotFound());
    }

    // ── delete ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onDelete_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Delete");

        mockMvc.perform(delete(adUrl)
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_staleIfMatchAfterUpdate_isPreconditionFailed() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = updatedAd(owner);

        mockMvc.perform(delete(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void admin_deletesAd_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Delete");

        mockMvc.perform(delete(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl)))
                .andExpect(status().isNoContent());
    }

    @Test
    void moderator_deletesAd_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Delete");

        mockMvc.perform(delete(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl)))
                .andExpect(status().isNoContent());
    }

    @Test
    void unknownAd_onDelete_isPreconditionFailed() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(delete(UNKNOWN_ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void delete_missingIfMatch_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Delete");

        // missing If-Match must be a bad request
        mockMvc.perform(delete(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Registers a user and promotes it to MODERATOR as the given administrator. */
    private RegisteredUser moderatorOf(RegisteredUser admin) throws Exception {
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        String userUrl = "/api/users/" + moderator.id();
        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Moderator","role":"MODERATOR"}"""))
                .andExpect(status().isOk());
        return moderator;
    }

    /** Creates one advertisement with the given title as the owner and returns its id. */
    private long createAd(RegisteredUser owner, String title) throws Exception {
        String createResponse = mockMvc.perform(post(ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"%s","description":"Fast","adKind":"OFFER"}""".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(createResponse);
    }

    /** Creates an advertisement as the owner, applies one update to it and returns its URL. */
    private String updatedAd(RegisteredUser owner) throws Exception {
        String adUrl = ADS + "/" + createAd(owner, "Original");
        mockMvc.perform(put(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isOk());
        return adUrl;
    }

    /** Returns the history list as the viewer sees it, newest entry first. */
    private List<Map<String, Object>> historyEntries(RegisteredUser viewer, String historyUrl) throws Exception {
        String json = mockMvc.perform(get(historyUrl).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> entries = JsonPath.read(json, "$");
        return entries;
    }

    /** Id of the oldest history snapshot, which is the last entry of the newest-first history. */
    private long oldestSnapshotId(RegisteredUser owner, String adUrl) throws Exception {
        Map<String, Object> oldest = historyEntries(owner, adUrl + "/history").getLast();
        return ((Number) oldest.get("snapshotId")).longValue();
    }

    /** Reads the resource's current ETag as the viewer, so a write can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
