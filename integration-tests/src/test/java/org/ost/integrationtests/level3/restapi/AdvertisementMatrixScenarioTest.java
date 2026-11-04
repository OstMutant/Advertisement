package org.ost.integrationtests.level3.restapi;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private static final String MERGE_PATCH = "application/merge-patch+json";

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

    // ── patch ────────────────────────────────────────────────────────────

    @Test
    void anonymous_onPatch_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Patch");

        mockMvc.perform(patch(adUrl)
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl))
                        .contentType(MERGE_PATCH).content("""
                                {"title":"Patched"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_patchesTitleOnly_keepsCategoriesAndCity() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long categoryId = createTaxon(admin, "CATEGORY", "Plumbing");
        long cityId = createTaxon(admin, "CITY", "Kyiv");
        String adUrl = createRichAd(owner, "[" + categoryId + "]", cityId);

        patchAs(owner, adUrl, etagOf(owner, adUrl), """
                {"title":"Patched"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Patched"));

        String json = getBody(adUrl);
        assertThat(JsonPath.<String>read(json, "$.title")).isEqualTo("Patched");
        assertThat(JsonPath.<String>read(json, "$.description")).isEqualTo("Fast");
        assertThat(categoryIdsOf(json)).containsExactly(categoryId);
        assertThat(((Number) JsonPath.read(json, "$.cityTaxonId")).longValue()).isEqualTo(cityId);
    }

    @Test
    void patch_nullCityTaxonId_clearsCityOnly() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long categoryId = createTaxon(admin, "CATEGORY", "Plumbing");
        long cityId = createTaxon(admin, "CITY", "Kyiv");
        String adUrl = createRichAd(owner, "[" + categoryId + "]", cityId);

        patchAs(owner, adUrl, etagOf(owner, adUrl), """
                {"cityTaxonId":null}""")
                .andExpect(status().isOk());

        mockMvc.perform(get(adUrl)).andExpect(jsonPath("$.cityTaxonId").doesNotExist());
        String json = getBody(adUrl);
        assertThat(categoryIdsOf(json)).containsExactly(categoryId);
        assertThat(JsonPath.<String>read(json, "$.title")).isEqualTo("Rich");
    }

    @Test
    void patch_categoryIds_replacesTheWholeSet() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long firstCategory = createTaxon(admin, "CATEGORY", "Plumbing");
        long secondCategory = createTaxon(admin, "CATEGORY", "Electrical");
        long cityId = createTaxon(admin, "CITY", "Kyiv");
        String adUrl = createRichAd(owner, "[" + firstCategory + "]", cityId);

        patchAs(owner, adUrl, etagOf(owner, adUrl), """
                {"categoryIds":[%d]}""".formatted(secondCategory))
                .andExpect(status().isOk());

        String json = getBody(adUrl);
        assertThat(categoryIdsOf(json)).containsExactly(secondCategory);
        assertThat(((Number) JsonPath.read(json, "$.cityTaxonId")).longValue()).isEqualTo(cityId);
    }

    @Test
    void patch_plainJsonContentType_isOk() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Patch");

        mockMvc.perform(patch(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"Patched"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Patched"));
    }

    @Test
    void stranger_onPatch_isForbidden() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        String adUrl = ADS + "/" + createAd(owner, "Patch");

        patchAs(stranger, adUrl, etagOf(owner, adUrl), """
                {"title":"Hijacked"}""")
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_patchesAd_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Patch");

        patchAs(admin, adUrl, etagOf(owner, adUrl), """
                {"title":"Patched"}""")
                .andExpect(status().isOk());
    }

    @Test
    void moderator_patchesAd_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Patch");

        patchAs(moderator, adUrl, etagOf(owner, adUrl), """
                {"title":"Patched"}""")
                .andExpect(status().isOk());
    }

    @Test
    void patch_staleIfMatch_isPreconditionFailed() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = updatedAd(owner);

        patchAs(owner, adUrl, STALE_IF_MATCH, """
                {"title":"Patched"}""")
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void patch_oldEtagAfterSuccessfulPatch_isPreconditionFailed() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Patch");
        String oldEtag = etagOf(owner, adUrl);

        patchAs(owner, adUrl, oldEtag, """
                {"title":"First"}""")
                .andExpect(status().isOk());

        patchAs(owner, adUrl, oldEtag, """
                {"title":"Second"}""")
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void patch_missingIfMatch_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Patch");

        // missing If-Match must be a bad request
        mockMvc.perform(patch(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MERGE_PATCH).content("""
                                {"title":"Patched"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_nullTitle_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Patch");

        patchAs(owner, adUrl, etagOf(owner, adUrl), """
                {"title":null}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void patch_unknownMember_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Patch");

        patchAs(owner, adUrl, etagOf(owner, adUrl), """
                {"cityTaxonID":5}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_textPlainContentType_isUnsupportedMediaType() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = ADS + "/" + createAd(owner, "Patch");

        String acceptPatch = mockMvc.perform(patch(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl))
                        .contentType(MediaType.TEXT_PLAIN).content("""
                                {"title":"Patched"}"""))
                .andExpect(status().isUnsupportedMediaType())
                .andReturn().getResponse().getHeader("Accept-Patch");
        if (acceptPatch != null) {
            assertThat(acceptPatch).contains(MERGE_PATCH);
        }
    }

    @Test
    void unknownAd_onPatch_isNotFound() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        patchAs(owner, UNKNOWN_ADS, STALE_IF_MATCH, """
                {"title":"Patched"}""")
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_addsExactlyOneHistoryEntry_withTheMergedState() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long categoryId = createTaxon(admin, "CATEGORY", "Plumbing");
        long cityId = createTaxon(admin, "CITY", "Kyiv");
        String adUrl = createRichAd(owner, "[" + categoryId + "]", cityId);
        int entriesBefore = historyEntries(owner, adUrl + "/history").size();

        patchAs(owner, adUrl, etagOf(owner, adUrl), """
                {"title":"Patched"}""")
                .andExpect(status().isOk());

        List<Map<String, Object>> entries = historyEntries(owner, adUrl + "/history");
        assertThat(entries).hasSize(entriesBefore + 1);
        long newestSnapshotId = ((Number) entries.getFirst().get("snapshotId")).longValue();
        mockMvc.perform(get(adUrl + "/history/" + newestSnapshotId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.title").value("Patched"))
                .andExpect(jsonPath("$.snapshot.description").value("Fast"))
                .andExpect(jsonPath("$.snapshot.cityTaxonId").value(cityId));
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

    /** Creates one advertisement with the given category id array literal and city as the owner and returns its URL. */
    private String createRichAd(RegisteredUser owner, String categoryIdsJson, long cityTaxonId) throws Exception {
        String createResponse = mockMvc.perform(post(ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"Rich","description":"Fast","adKind":"OFFER","categoryIds":%s,"cityTaxonId":%d}""".formatted(categoryIdsJson, cityTaxonId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ADS + "/" + JsonScenarioUtils.extractId(createResponse);
    }

    /** Creates a taxon of the given type with one name as the administrator and returns its id. */
    private long createTaxon(RegisteredUser admin, String type, String name) throws Exception {
        String response = mockMvc.perform(post("/api/taxons")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type":"%s","translations":[{"locale":"en","name":"%s","description":"%s"},{"locale":"uk","name":"%s","description":"%s"}]}"""
                                .formatted(type, name, name, name, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(response);
    }

    /** Sends a merge patch to the advertisement as the caller with the given If-Match value. */
    private ResultActions patchAs(RegisteredUser caller, String adUrl, String ifMatch, String body) throws Exception {
        return mockMvc.perform(patch(adUrl)
                .header(HttpHeaders.AUTHORIZATION, bearer(caller))
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MERGE_PATCH).content(body));
    }

    /** Reads the resource as an anonymous caller and returns the raw JSON body. */
    private String getBody(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static List<Long> categoryIdsOf(String json) {
        List<Number> ids = JsonPath.read(json, "$.categoryIds");
        return ids.stream().map(Number::longValue).toList();
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
