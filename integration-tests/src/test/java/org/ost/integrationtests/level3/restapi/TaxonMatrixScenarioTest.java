package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 matrix: every taxon REST operation across the anonymous, plain, moderator and administrator roles, with its validation and precondition edges. */
@Level3ScenarioTest
class TaxonMatrixScenarioTest extends AbstractRestApiScenarioTest {

    private static final String TAXONS = "/api/taxons";
    private static final String UNKNOWN_ID = "999999";
    private static final String UNKNOWN_TAXON = TAXONS + "/" + UNKNOWN_ID;
    private static final String VALID_BODY = """
            {"type":"CATEGORY","translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"},{"locale":"uk","name":"Сантехніка","description":"Сантехнічні послуги"}]}""";
    private static final String UPDATE_BODY = """
            {"translations":[{"locale":"en","name":"Electrics","description":"Electrics services"},{"locale":"uk","name":"Електрика2","description":"Електро"}]}""";
    private static final String STALE_IF_MATCH = "\"0\"";

    // ── create ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onCreate_isUnauthorized() throws Exception {
        mockMvc.perform(post(TAXONS).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void moderator_createsTaxon_isCreated() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);

        postTaxon(moderator, VALID_BODY).andExpect(status().isCreated());
    }

    @Test
    void create_missingType_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        postTaxon(admin, """
                {"translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"},{"locale":"uk","name":"Сантехніка","description":"Сантехнічні послуги"}]}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_unknownType_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        postTaxon(admin, """
                {"type":"FOO","translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"},{"locale":"uk","name":"Сантехніка","description":"Сантехнічні послуги"}]}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingTranslations_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        postTaxon(admin, """
                {"type":"CATEGORY"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_onlyEnglishTranslation_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        postTaxon(admin, """
                {"type":"CATEGORY","translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"}]}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankEnglishName_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        postTaxon(admin, """
                {"type":"CATEGORY","translations":[{"locale":"en","name":"","description":"Plumbing services"},{"locale":"uk","name":"Сантехніка","description":"Сантехнічні послуги"}]}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankEnglishDescription_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        postTaxon(admin, """
                {"type":"CATEGORY","translations":[{"locale":"en","name":"Plumbing","description":""},{"locale":"uk","name":"Сантехніка","description":"Сантехнічні послуги"}]}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_englishTranslationWithoutName_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        postTaxon(admin, """
                {"type":"CATEGORY","translations":[{"locale":"en","description":"Plumbing services"},{"locale":"uk","name":"Сантехніка","description":"Сантехнічні послуги"}]}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicateEnglishEntries_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        postTaxon(admin, """
                {"type":"CATEGORY","translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"},{"locale":"en","name":"Plumbers","description":"Plumber services"},{"locale":"uk","name":"Сантехніка","description":"Сантехнічні послуги"}]}""")
                .andExpect(status().isBadRequest());
    }

    // ── get ──────────────────────────────────────────────────────────────

    @Test
    void unknownTaxon_get_isNotFound() throws Exception {
        mockMvc.perform(get(UNKNOWN_TAXON))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_withUkrainianLocale_returnsUkrainianName() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        mockMvc.perform(get(taxonUrl(taxonId)).param("locale", "uk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Сантехніка"));
    }

    @Test
    void get_withUnsupportedLocale_fallsBackToEnglishName() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        mockMvc.perform(get(taxonUrl(taxonId)).param("locale", "xx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Plumbing"));
    }

    // ── delete ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onDelete_isUnauthorized() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        mockMvc.perform(delete(taxonUrl(taxonId))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, taxonUrl(taxonId))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void plainUser_onDelete_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser plain = registerUserAndIssueApiKey("Plain");
        long taxonId = createTaxon(admin);

        deleteTaxon(plain, taxonId, etagOf(admin, taxonUrl(taxonId))).andExpect(status().isForbidden());
    }

    @Test
    void admin_deletesTaxon_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        deleteTaxon(admin, taxonId, etagOf(admin, taxonUrl(taxonId))).andExpect(status().isNoContent());
    }

    @Test
    void moderator_deletesTaxon_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        long taxonId = createTaxon(admin);

        deleteTaxon(moderator, taxonId, etagOf(admin, taxonUrl(taxonId))).andExpect(status().isNoContent());
    }

    @Test
    void delete_staleIfMatchAfterUpdate_isPreconditionFailed() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);
        updateTaxon(admin, taxonId, etagOf(admin, taxonUrl(taxonId)), UPDATE_BODY).andExpect(status().isOk());

        deleteTaxon(admin, taxonId, STALE_IF_MATCH).andExpect(status().isPreconditionFailed());
    }

    @Test
    void unknownTaxon_onDelete_isPreconditionFailed() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        deleteTaxon(admin, Long.parseLong(UNKNOWN_ID), STALE_IF_MATCH).andExpect(status().isPreconditionFailed());
    }

    @Test
    void delete_missingIfMatch_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        // missing If-Match must be a bad request
        mockMvc.perform(delete(taxonUrl(taxonId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_deletesAlreadyDeletedTaxon_isPreconditionFailed() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);
        String etag = etagOf(admin, taxonUrl(taxonId));
        deleteTaxon(admin, taxonId, etag).andExpect(status().isNoContent());

        deleteTaxon(admin, taxonId, etag).andExpect(status().isPreconditionFailed());
    }

    // ── restore ──────────────────────────────────────────────────────────

    @Test
    void anonymous_onRestore_isUnauthorized() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createDeletedTaxon(admin);

        mockMvc.perform(post(taxonUrl(taxonId) + "/restore"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void plainUser_onRestore_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser plain = registerUserAndIssueApiKey("Plain");
        long taxonId = createDeletedTaxon(admin);

        restoreTaxon(plain, taxonId).andExpect(status().isForbidden());
    }

    @Test
    void moderator_restoresDeletedTaxon_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        long taxonId = createDeletedTaxon(admin);

        restoreTaxon(moderator, taxonId).andExpect(status().isOk());
    }

    // ── update ───────────────────────────────────────────────────────────

    @Test
    void update_missingIfMatch_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        // missing If-Match must be a bad request
        mockMvc.perform(put(taxonUrl(taxonId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_missingTranslations_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);

        updateTaxon(admin, taxonId, etagOf(admin, taxonUrl(taxonId)), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void update_duplicateEnglishEntries_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long taxonId = createTaxon(admin);
        String body = """
                {"translations":[{"locale":"en","name":"Electrics","description":"Electrics services"},{"locale":"en","name":"Electrical","description":"Electrical services"},{"locale":"uk","name":"Електрика2","description":"Електро"}]}""";

        updateTaxon(admin, taxonId, etagOf(admin, taxonUrl(taxonId)), body).andExpect(status().isBadRequest());
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

    /** Creates one CATEGORY taxon as the given administrator and returns its id. */
    private long createTaxon(RegisteredUser admin) throws Exception {
        String createResponse = postTaxon(admin, VALID_BODY)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(createResponse);
    }

    /** Creates one taxon as the administrator, deletes it with its current ETag and returns its id. */
    private long createDeletedTaxon(RegisteredUser admin) throws Exception {
        long taxonId = createTaxon(admin);
        deleteTaxon(admin, taxonId, etagOf(admin, taxonUrl(taxonId))).andExpect(status().isNoContent());
        return taxonId;
    }

    /** Sends one taxon create as the actor with the given body. */
    private ResultActions postTaxon(RegisteredUser actor, String body) throws Exception {
        return mockMvc.perform(post(TAXONS)
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Sends one taxon update as the actor with the given If-Match value and body. */
    private ResultActions updateTaxon(RegisteredUser actor, long taxonId, String ifMatch, String body) throws Exception {
        return mockMvc.perform(put(taxonUrl(taxonId))
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Sends one taxon soft-delete as the actor with the given If-Match value. */
    private ResultActions deleteTaxon(RegisteredUser actor, long taxonId, String ifMatch) throws Exception {
        return mockMvc.perform(delete(taxonUrl(taxonId))
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .header(HttpHeaders.IF_MATCH, ifMatch));
    }

    /** Sends one restore of a soft-deleted taxon as the actor. */
    private ResultActions restoreTaxon(RegisteredUser actor, long taxonId) throws Exception {
        return mockMvc.perform(post(taxonUrl(taxonId) + "/restore")
                .header(HttpHeaders.AUTHORIZATION, bearer(actor)));
    }

    /** Reads the resource's current ETag as the viewer, so a write can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static String taxonUrl(long taxonId) {
        return TAXONS + "/" + taxonId;
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
