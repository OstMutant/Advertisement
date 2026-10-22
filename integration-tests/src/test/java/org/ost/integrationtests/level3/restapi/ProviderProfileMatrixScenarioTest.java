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

/** Level 3 matrix: every provider-profile REST operation across the anonymous, owner, stranger, moderator and administrator roles, with its validation and precondition edges. */
@Level3ScenarioTest
class ProviderProfileMatrixScenarioTest extends AbstractRestApiScenarioTest {

    private static final String PROFILES = "/api/provider-profiles";
    private static final String UNKNOWN_ID = "999999";
    private static final String UNKNOWN_PROFILE = PROFILES + "/" + UNKNOWN_ID;
    private static final String VALID_BODY = """
            {"kind":"MASTER","about":"Experienced plumber","phone":"+380501234567","telegram":"ivan_master"}""";
    private static final String UPDATED_BODY = """
            {"kind":"MASTER","about":"Experienced plumber, now also water heaters","phone":"+380501234567","telegram":"ivan_master"}""";
    private static final String STALE_IF_MATCH = "\"0\"";

    // ── list ─────────────────────────────────────────────────────────────

    @Test
    void list_sortByUnknownField_isBadRequest() throws Exception {
        mockMvc.perform(get(PROFILES).param("sort", "bogus,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_unknownKind_isBadRequest() throws Exception {
        mockMvc.perform(get(PROFILES).param("kinds", "FOO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_createdAtStartAfterEnd_isBadRequest() throws Exception {
        mockMvc.perform(get(PROFILES)
                        .param("createdAtStart", "2030-01-02T00:00:00Z")
                        .param("createdAtEnd", "2030-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_updatedAtStartAfterEnd_isBadRequest() throws Exception {
        mockMvc.perform(get(PROFILES)
                        .param("updatedAtStart", "2030-01-02T00:00:00Z")
                        .param("updatedAtEnd", "2030-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_negativePage_isBadRequest() throws Exception {
        mockMvc.perform(get(PROFILES).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    // ── create ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onCreate_isUnauthorized() throws Exception {
        mockMvc.perform(post(PROFILES).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_missingKind_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"about":"Experienced plumber"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_unknownKind_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"FOO","about":"Experienced plumber"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_aboutLongerThanLimit_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"MASTER","about":"%s"}""".formatted("a".repeat(20001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_moreThanTenCategories_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"MASTER","about":"Experienced plumber","categoryIds":[1,2,3,4,5,6,7,8,9,10,11]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidPhone_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"MASTER","about":"Experienced plumber","phone":"123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidTelegram_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"MASTER","about":"Experienced plumber","telegram":"ab"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidViber_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"MASTER","about":"Experienced plumber","viber":"abc"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void plainUser_createsSupportProfile_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"SUPPORT","about":"Support desk"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderator_createsSupportProfile_isCreated() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"SUPPORT","about":"Support desk"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void owner_createsSecondProfile_isConflict() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        createProfile(owner, VALID_BODY);

        mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void admin_createsProfile_isCreated() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    // ── get ──────────────────────────────────────────────────────────────

    @Test
    void unknownProfile_get_isNotFound() throws Exception {
        mockMvc.perform(get(UNKNOWN_PROFILE))
                .andExpect(status().isNotFound());
    }

    // ── history ──────────────────────────────────────────────────────────

    @Test
    void anonymous_onHistory_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        mockMvc.perform(get(profileUrl + "/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_seesHistory_isOk() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        assertThat(historyEntries(owner, profileUrl + "/history")).isNotEmpty();
    }

    @Test
    void stranger_seesEmptyHistory_isOk() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        mockMvc.perform(get(profileUrl + "/history").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void admin_seesHistory_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        assertThat(historyEntries(admin, profileUrl + "/history")).isNotEmpty();
    }

    @Test
    void moderator_seesHistory_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        assertThat(historyEntries(moderator, profileUrl + "/history")).isNotEmpty();
    }

    @Test
    void admin_onUnknownProfileHistory_isOkWithEmptyList() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(UNKNOWN_PROFILE + "/history").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── historySnapshot ──────────────────────────────────────────────────

    @Test
    void anonymous_onHistorySnapshot_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);
        long oldest = oldestSnapshotId(owner, profileUrl);

        mockMvc.perform(get(profileUrl + "/history/" + oldest))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_readsSnapshot_isOk() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);
        long oldest = oldestSnapshotId(owner, profileUrl);

        mockMvc.perform(get(profileUrl + "/history/" + oldest).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void admin_readsSnapshot_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);
        long oldest = oldestSnapshotId(owner, profileUrl);

        mockMvc.perform(get(profileUrl + "/history/" + oldest).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void moderator_readsSnapshot_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);
        long oldest = oldestSnapshotId(owner, profileUrl);

        mockMvc.perform(get(profileUrl + "/history/" + oldest).header(HttpHeaders.AUTHORIZATION, bearer(moderator)))
                .andExpect(status().isOk());
    }

    // ── update ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onUpdate_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        mockMvc.perform(put(profileUrl)
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void moderator_updatesProfile_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        mockMvc.perform(put(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void update_missingKind_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        mockMvc.perform(put(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"about":"Experienced plumber"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_aboutLongerThanLimit_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        mockMvc.perform(put(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"MASTER","about":"%s"}""".formatted("a".repeat(20001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_invalidPhone_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        mockMvc.perform(put(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"MASTER","about":"Experienced plumber","phone":"123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProfile_onUpdate_isPreconditionFailed() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(put(UNKNOWN_PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH)
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void update_staleIfMatch_isPreconditionFailed() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = updatedProfile(owner);

        mockMvc.perform(put(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH)
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void update_missingIfMatch_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        // missing If-Match must be a bad request
        mockMvc.perform(put(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isBadRequest());
    }

    // ── restore ──────────────────────────────────────────────────────────

    @Test
    void anonymous_onRestore_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = updatedProfile(owner);
        long oldest = oldestSnapshotId(owner, profileUrl);

        mockMvc.perform(post(profileUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_restoresSnapshot_isOk() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = updatedProfile(owner);
        long oldest = oldestSnapshotId(owner, profileUrl);

        mockMvc.perform(post(profileUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl)))
                .andExpect(status().isOk());
    }

    @Test
    void admin_restoresSnapshot_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = updatedProfile(owner);
        long oldest = oldestSnapshotId(owner, profileUrl);

        mockMvc.perform(post(profileUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl)))
                .andExpect(status().isOk());
    }

    @Test
    void moderator_restoresSnapshot_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = updatedProfile(owner);
        long oldest = oldestSnapshotId(owner, profileUrl);

        mockMvc.perform(post(profileUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl)))
                .andExpect(status().isOk());
    }

    @Test
    void restore_staleIfMatch_isPreconditionFailed() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = updatedProfile(owner);
        long oldest = oldestSnapshotId(owner, profileUrl);

        mockMvc.perform(post(profileUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void restore_missingIfMatch_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = updatedProfile(owner);
        long oldest = oldestSnapshotId(owner, profileUrl);

        mockMvc.perform(post(profileUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_onUnknownSnapshotRestore_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = updatedProfile(owner);

        mockMvc.perform(post(profileUrl + "/history/" + UNKNOWN_ID + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl)))
                .andExpect(status().isNotFound());
    }

    // ── delete ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onDelete_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        mockMvc.perform(delete(profileUrl)
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_deletesProfile_isNoContent() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        mockMvc.perform(delete(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl)))
                .andExpect(status().isNoContent());
    }

    @Test
    void admin_deletesProfile_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        mockMvc.perform(delete(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl)))
                .andExpect(status().isNoContent());
    }

    @Test
    void moderator_deletesProfile_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        mockMvc.perform(delete(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl)))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_staleIfMatch_isPreconditionFailed() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = updatedProfile(owner);

        mockMvc.perform(delete(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void unknownProfile_onDelete_isPreconditionFailed() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(delete(UNKNOWN_PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void delete_missingIfMatch_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);

        // missing If-Match must be a bad request
        mockMvc.perform(delete(profileUrl)
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

    /** Creates one provider profile from the given body as the owner and returns its id. */
    private long createProfile(RegisteredUser owner, String body) throws Exception {
        String createResponse = mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(createResponse);
    }

    /** Creates a provider profile as the owner, applies one update to it and returns its URL. */
    private String updatedProfile(RegisteredUser owner) throws Exception {
        String profileUrl = PROFILES + "/" + createProfile(owner, VALID_BODY);
        mockMvc.perform(put(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isOk());
        return profileUrl;
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
    private long oldestSnapshotId(RegisteredUser owner, String profileUrl) throws Exception {
        Map<String, Object> oldest = historyEntries(owner, profileUrl + "/history").getLast();
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
