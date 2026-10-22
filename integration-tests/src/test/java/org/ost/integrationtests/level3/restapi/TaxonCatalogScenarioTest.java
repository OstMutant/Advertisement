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
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: the taxon catalog over REST -- public listing by type and locale, admin and moderator updates with optimistic locking, and history with snapshot restore. */
@Level3ScenarioTest
class TaxonCatalogScenarioTest extends AbstractRestApiScenarioTest {

    private static final String TAXON_BODY = """
            {"type":"CATEGORY","translations":[{"locale":"en","name":"Electrical","description":"Electrical services"},{"locale":"uk","name":"Електрика","description":"Електромонтажні послуги"}]}""";
    private static final String UPDATE_BODY = """
            {"translations":[{"locale":"en","name":"Electrics","description":"Electrics services"},{"locale":"uk","name":"Електрика2","description":"Електро"}]}""";

    @Test
    void visitor_listsCategoriesInEnglish() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        mockMvc.perform(get("/api/taxons").param("type", "CATEGORY").param("locale", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + taxonId + ")].name").value(hasItem("Electrical")));
    }

    @Test
    void visitor_listsCategoriesInUkrainian() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        mockMvc.perform(get("/api/taxons").param("type", "CATEGORY").param("locale", "uk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + taxonId + ")].name").value(hasItem("Електрика")));
    }

    @Test
    void listingByCity_doesNotContainCategory() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        mockMvc.perform(get("/api/taxons").param("type", "CITY").param("locale", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + taxonId + ")]").isEmpty());
    }

    @Test
    void listWithUnknownType_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/taxons").param("type", "FOO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void administrator_updatesTaxon_andEnglishNameChanges() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        updateTaxon(admin, taxonId, etagOf(admin, taxonUrl(taxonId)), UPDATE_BODY).andExpect(status().isOk());

        mockMvc.perform(get(taxonUrl(taxonId)).param("locale", "en"))
                .andExpect(jsonPath("$.name").value("Electrics"));
    }

    @Test
    void moderator_updatesTaxon() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        promoteToModerator(admin, moderator, "Moderator");
        long taxonId = createTaxon(admin);

        updateTaxon(moderator, taxonId, etagOf(moderator, taxonUrl(taxonId)), UPDATE_BODY).andExpect(status().isOk());
    }

    @Test
    void plainUser_updatingTaxon_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser plain = registerUserAndIssueApiKey("Plain");
        long taxonId = createTaxon(admin);

        updateTaxon(plain, taxonId, etagOf(plain, taxonUrl(taxonId)), UPDATE_BODY).andExpect(status().isForbidden());
    }

    @Test
    void anonymous_updatingTaxon_isUnauthorized() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        mockMvc.perform(put(taxonUrl(taxonId))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, taxonUrl(taxonId)))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankEnglishName_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);
        String body = """
                {"translations":[{"locale":"en","name":"","description":"Electrics services"},{"locale":"uk","name":"Електрика2","description":"Електро"}]}""";

        updateTaxon(admin, taxonId, etagOf(admin, taxonUrl(taxonId)), body).andExpect(status().isBadRequest());
    }

    @Test
    void onlyEnglishTranslation_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);
        String body = """
                {"translations":[{"locale":"en","name":"Electrics","description":"Electrics services"}]}""";

        updateTaxon(admin, taxonId, etagOf(admin, taxonUrl(taxonId)), body).andExpect(status().isBadRequest());
    }

    @Test
    void unknownTaxon_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        updateTaxon(admin, 999999L, "\"0\"", UPDATE_BODY).andExpect(status().isBadRequest());
    }

    @Test
    void staleVersion_isPreconditionFailed() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);
        updateTaxon(admin, taxonId, etagOf(admin, taxonUrl(taxonId)), UPDATE_BODY).andExpect(status().isOk());

        // The row is at version 1 after the update above, so "0" is stale.
        updateTaxon(admin, taxonId, "\"0\"", UPDATE_BODY).andExpect(status().isPreconditionFailed());
    }

    @Test
    void administrator_readsTaxonHistory_withSnapshotIds() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxonUpdatedOnce(admin);

        List<Map<String, Object>> entries = historyEntries(admin, taxonUrl(taxonId) + "/history");
        assertThat(entries).hasSizeGreaterThanOrEqualTo(2);
        assertThat(entries).allSatisfy(entry -> assertThat(entry).containsKey("snapshotId"));
    }

    @Test
    void moderator_readsTaxonHistory() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        promoteToModerator(admin, moderator, "Moderator");
        long taxonId = createTaxonUpdatedOnce(admin);

        assertThat(historyEntries(moderator, taxonUrl(taxonId) + "/history")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void plainUser_seesEmptyTaxonHistory() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser plain = registerUserAndIssueApiKey("Plain");
        long taxonId = createTaxonUpdatedOnce(admin);

        mockMvc.perform(get(taxonUrl(taxonId) + "/history").header(HttpHeaders.AUTHORIZATION, bearer(plain)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void anonymous_onTaxonHistory_isUnauthorized() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxonUpdatedOnce(admin);

        mockMvc.perform(get(taxonUrl(taxonId) + "/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownTaxon_hasEmptyHistory() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get("/api/taxons/999999/history").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void administrator_readsTaxonHistorySnapshot() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxonUpdatedOnce(admin);

        mockMvc.perform(get(taxonUrl(taxonId) + "/history/" + oldestSnapshotId(admin, taxonId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.snapshot").exists());
    }

    @Test
    void moderator_readsTaxonHistorySnapshot() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        promoteToModerator(admin, moderator, "Moderator");
        long taxonId = createTaxonUpdatedOnce(admin);

        mockMvc.perform(get(taxonUrl(taxonId) + "/history/" + oldestSnapshotId(admin, taxonId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator)))
                .andExpect(status().isOk());
    }

    @Test
    void plainUser_readingTaxonHistorySnapshot_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser plain = registerUserAndIssueApiKey("Plain");
        long taxonId = createTaxonUpdatedOnce(admin);

        mockMvc.perform(get(taxonUrl(taxonId) + "/history/" + oldestSnapshotId(admin, taxonId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(plain)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_onTaxonHistorySnapshot_isUnauthorized() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxonUpdatedOnce(admin);

        mockMvc.perform(get(taxonUrl(taxonId) + "/history/" + oldestSnapshotId(admin, taxonId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownSnapshot_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxonUpdatedOnce(admin);

        mockMvc.perform(get(taxonUrl(taxonId) + "/history/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void administrator_restoresOldestSnapshot() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxonUpdatedOnce(admin);
        String taxonUrl = taxonUrl(taxonId);

        mockMvc.perform(post(taxonUrl + "/history/" + oldestSnapshotId(admin, taxonId) + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, taxonUrl)))
                .andExpect(status().isOk());

        mockMvc.perform(get(taxonUrl).param("locale", "en"))
                .andExpect(jsonPath("$.name").value("Electrical"));
    }

    @Test
    void moderator_restoresOldestSnapshotAfterOwnUpdate() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        promoteToModerator(admin, moderator, "Moderator");
        long taxonId = createTaxon(admin);
        String taxonUrl = taxonUrl(taxonId);
        updateTaxon(moderator, taxonId, etagOf(moderator, taxonUrl), UPDATE_BODY).andExpect(status().isOk());

        mockMvc.perform(post(taxonUrl + "/history/" + oldestSnapshotId(admin, taxonId) + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator))
                        .header(HttpHeaders.IF_MATCH, etagOf(moderator, taxonUrl)))
                .andExpect(status().isOk());
    }

    @Test
    void plainUser_restoringTaxonSnapshot_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser plain = registerUserAndIssueApiKey("Plain");
        long taxonId = createTaxonUpdatedOnce(admin);
        String taxonUrl = taxonUrl(taxonId);

        mockMvc.perform(post(taxonUrl + "/history/" + oldestSnapshotId(admin, taxonId) + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(plain))
                        .header(HttpHeaders.IF_MATCH, etagOf(plain, taxonUrl)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_restoringTaxonSnapshot_isUnauthorized() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxonUpdatedOnce(admin);
        String taxonUrl = taxonUrl(taxonId);

        mockMvc.perform(post(taxonUrl + "/history/" + oldestSnapshotId(admin, taxonId) + "/restore")
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, taxonUrl)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restoreWithStaleVersion_isPreconditionFailed() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxonUpdatedOnce(admin);

        mockMvc.perform(post(taxonUrl(taxonId) + "/history/" + oldestSnapshotId(admin, taxonId) + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void restoreWithoutIfMatch_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxonUpdatedOnce(admin);

        mockMvc.perform(post(taxonUrl(taxonId) + "/history/" + oldestSnapshotId(admin, taxonId) + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownSnapshot_restoreIsNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxonUpdatedOnce(admin);
        String taxonUrl = taxonUrl(taxonId);

        mockMvc.perform(post(taxonUrl + "/history/999999/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, taxonUrl)))
                .andExpect(status().isNotFound());
    }

    /** Creates one CATEGORY taxon as the given administrator and returns its id. */
    private long createTaxon(RegisteredUser admin) throws Exception {
        String createResponse = mockMvc.perform(post("/api/taxons")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(TAXON_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(createResponse);
    }

    /** Creates one taxon and renames it once, so its history holds at least two entries. */
    private long createTaxonUpdatedOnce(RegisteredUser admin) throws Exception {
        long taxonId = createTaxon(admin);
        updateTaxon(admin, taxonId, etagOf(admin, taxonUrl(taxonId)), UPDATE_BODY).andExpect(status().isOk());
        return taxonId;
    }

    /** Sends one taxon update as the actor with the given If-Match value and body. */
    private ResultActions updateTaxon(RegisteredUser actor, long taxonId, String ifMatch, String body) throws Exception {
        return mockMvc.perform(put(taxonUrl(taxonId))
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MediaType.APPLICATION_JSON).content(body));
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

    /** Returns the id of the oldest history entry of the taxon, as the viewer sees it. */
    private long oldestSnapshotId(RegisteredUser viewer, long taxonId) throws Exception {
        return snapshotIdOf(historyEntries(viewer, taxonUrl(taxonId) + "/history").getLast());
    }

    /** Returns the history list as the viewer sees it, newest entry first. */
    private List<Map<String, Object>> historyEntries(RegisteredUser viewer, String historyUrl) throws Exception {
        String json = mockMvc.perform(get(historyUrl).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> entries = JsonPath.read(json, "$");
        return entries;
    }

    private static long snapshotIdOf(Map<String, Object> historyEntry) {
        return ((Number) historyEntry.get("snapshotId")).longValue();
    }

    /** Reads the resource's current ETag as the viewer, so an update or restore can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static String taxonUrl(long taxonId) {
        return "/api/taxons/" + taxonId;
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
