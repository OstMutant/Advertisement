package org.ost.integrationtests.level3.restapi;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: which credential each endpoint accepts on the real security chains, and the failed-login limit on API-key issuance. */
@Level3ScenarioTest
class ApiAuthenticationChainScenarioTest extends AbstractRestApiScenarioTest {

    @Test
    void basicCredentials_onABearerOnlyEndpoint_getUnauthorizedWithBearerChallenge_notServerError() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Basic Caller");

        mockMvc.perform(get("/api/api-keys").header(HttpHeaders.AUTHORIZATION, basic(user.email(), user.password())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, not(containsString("Basic"))));
    }

    @Test
    void basicCredentials_onABearerOnlyWrite_getUnauthorized() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Basic Writer");

        mockMvc.perform(post("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, basic(user.email(), user.password()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plumbing","description":"Fast","adKind":"OFFER"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void basicCredentials_onAPublicRead_areIgnored() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Basic Reader");

        mockMvc.perform(get("/api/advertisements").header(HttpHeaders.AUTHORIZATION, basic(user.email(), user.password())))
                .andExpect(status().isOk());
    }

    @Test
    void missingBearerKey_getsBareBearerChallenge_andAFailedOneGetsInvalidToken() throws Exception {
        registerUserAndIssueApiKey("Challenge");

        mockMvc.perform(get("/api/api-keys"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"api\""));
        mockMvc.perform(get("/api/api-keys").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"api\", error=\"invalid_token\""));
    }

    @Test
    void bearerKey_onKeyIssuance_getsUnauthorizedWithBasicChallenge_notServerError() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Bearer Issuer");

        mockMvc.perform(post("/api/api-keys").header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Basic")));
    }

    @Test
    void validBasic_onKeyIssuance_returnsTheKey_withoutCaching() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Key Owner");

        mockMvc.perform(post("/api/api-keys").header(HttpHeaders.AUTHORIZATION, basic(user.email(), user.password())))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.rawKey").isString());
    }

    @Test
    void wrongPasswords_areLimitedPerEmail_andOtherEmailsAreUnaffected() throws Exception {
        RegisteredUser victim = registerUserAndIssueApiKey("Victim");
        RegisteredUser bystander = registerUserAndIssueApiKey("Bystander");

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/api/api-keys").header(HttpHeaders.AUTHORIZATION, basic(victim.email(), "wrong-" + attempt)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Basic")));
        }

        String blockedBody = mockMvc.perform(post("/api/api-keys").header(HttpHeaders.AUTHORIZATION, basic(victim.email(), "wrong-6")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"))
                .andReturn().getResponse().getContentAsString();
        assertThat(blockedBody).contains("Too many failed login attempts");

        mockMvc.perform(post("/api/api-keys").header(HttpHeaders.AUTHORIZATION, basic(victim.email(), victim.password())))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/api/api-keys").header(HttpHeaders.AUTHORIZATION, basic(bystander.email(), bystander.password())))
                .andExpect(status().isCreated());
    }

    @Test
    void revokingAKeyTwice_secondAttemptIsConflict() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Revoke Twice");
        long firstKeyId = firstKeyId(user.rawApiKey());
        String secondRawKey = JsonPath.read(mockMvc.perform(post("/api/api-keys").header(HttpHeaders.AUTHORIZATION, basic(user.email(), user.password())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.rawKey");

        mockMvc.perform(delete("/api/api-keys/" + firstKeyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + secondRawKey))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/api-keys/" + firstKeyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + secondRawKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("API key is already revoked"));
    }

    @Test
    void revokingAnotherUsersKey_isNotFound() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Key Holder");
        RegisteredUser intruder = registerUserAndIssueApiKey("Intruder");
        long ownersKeyId = firstKeyId(owner.rawApiKey());

        mockMvc.perform(delete("/api/api-keys/" + ownersKeyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.rawApiKey()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found."));
    }

    @Test
    void revokedKey_isHiddenFromTheList_unlessRequested() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Hidden Key");
        long revokedKeyId = firstKeyId(user.rawApiKey());
        String activeRawKey = JsonPath.read(mockMvc.perform(post("/api/api-keys").header(HttpHeaders.AUTHORIZATION, basic(user.email(), user.password())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.rawKey");

        mockMvc.perform(delete("/api/api-keys/" + revokedKeyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + activeRawKey))
                .andExpect(status().isNoContent());

        String listBody = mockMvc.perform(get("/api/api-keys").header(HttpHeaders.AUTHORIZATION, "Bearer " + activeRawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(listBody, "$[0].id")).longValue()).isNotEqualTo(revokedKeyId);

        String listWithRevokedBody = mockMvc.perform(get("/api/api-keys?includeRevoked=true").header(HttpHeaders.AUTHORIZATION, "Bearer " + activeRawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        List<Object> revokedRows = JsonPath.read(listWithRevokedBody, "$[?(@.revokedAt != null)]");
        assertThat(revokedRows).hasSize(1);
    }

    private long firstKeyId(String rawKey) throws Exception {
        String body = mockMvc.perform(get("/api/api-keys").header(HttpHeaders.AUTHORIZATION, "Bearer " + rawKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$[0].id")).longValue();
    }

    private static String basic(String email, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
