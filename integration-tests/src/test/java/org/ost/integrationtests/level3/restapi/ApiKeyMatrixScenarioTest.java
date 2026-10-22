package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 matrix: API-key issuance, listing and revocation across the anonymous, bearer and basic credential shapes, with their validation edges. */
@Level3ScenarioTest
class ApiKeyMatrixScenarioTest extends AbstractRestApiScenarioTest {

    private static final String API_KEYS = "/api/api-keys";
    private static final String UNKNOWN_ID = "999999";

    // ── issue ────────────────────────────────────────────────────────────

    @Test
    void anonymous_onIssue_isUnauthorized() throws Exception {
        mockMvc.perform(post(API_KEYS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void basic_withWrongPassword_isUnauthorized() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Wrong Password");

        mockMvc.perform(post(API_KEYS).header(HttpHeaders.AUTHORIZATION, basic(user.email(), "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void basic_withoutBody_isCreated() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("No Body");

        mockMvc.perform(post(API_KEYS).header(HttpHeaders.AUTHORIZATION, basic(user.email(), user.password())))
                .andExpect(status().isCreated());
    }

    @Test
    void basic_withLabel_isCreated() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("With Label");

        mockMvc.perform(post(API_KEYS)
                        .header(HttpHeaders.AUTHORIZATION, basic(user.email(), user.password()))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"label":"laptop"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void basic_withLabelLongerThan100_isBadRequest() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Long Label");

        mockMvc.perform(post(API_KEYS)
                        .header(HttpHeaders.AUTHORIZATION, basic(user.email(), user.password()))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"label":"%s"}""".formatted("a".repeat(101))))
                .andExpect(status().isBadRequest());
    }

    // ── list ─────────────────────────────────────────────────────────────

    @Test
    void anonymous_onList_isUnauthorized() throws Exception {
        mockMvc.perform(get(API_KEYS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerKey_onList_isUnauthorized() throws Exception {
        mockMvc.perform(get(API_KEYS).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withNonBooleanIncludeRevoked_isBadRequest() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(get(API_KEYS + "?includeRevoked=abc").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_showsOnlyTheCallersOwnKeys() throws Exception {
        RegisteredUser userA = registerUserAndIssueApiKey("User A");
        RegisteredUser userB = registerUserAndIssueApiKey("User B");
        mockMvc.perform(post(API_KEYS).header(HttpHeaders.AUTHORIZATION, basic(userB.email(), userB.password())))
                .andExpect(status().isCreated());

        mockMvc.perform(get(API_KEYS).header(HttpHeaders.AUTHORIZATION, bearer(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── revoke ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onRevoke_isUnauthorized() throws Exception {
        mockMvc.perform(delete(API_KEYS + "/" + UNKNOWN_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revoke_nonNumericId_isBadRequest() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(delete(API_KEYS + "/abc").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revoke_unknownId_isNotFound() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(delete(API_KEYS + "/" + UNKNOWN_ID).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }

    private static String basic(String email, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
