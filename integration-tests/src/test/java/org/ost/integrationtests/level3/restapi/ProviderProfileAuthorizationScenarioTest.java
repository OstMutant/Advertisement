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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: provider-profile ownership rules -- non-owners are forbidden from writes, the owner and an administrator may update, and a non-owner cannot restore a snapshot. */
@Level3ScenarioTest
class ProviderProfileAuthorizationScenarioTest extends AbstractRestApiScenarioTest {

    private static final String HIJACKED_BODY = """
            {"kind":"MASTER","about":"Hijacked"}""";

    @Test
    void nonOwner_cannotUpdateSomeoneElsesProfile() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        String profileUrl = "/api/provider-profiles/" + createProviderProfile(owner);

        mockMvc.perform(put(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .header(HttpHeaders.IF_MATCH, etagOf(stranger, profileUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(HIJACKED_BODY))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(profileUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.about").value("Experienced plumber"));
    }

    @Test
    void owner_canUpdateOwnProfile() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = "/api/provider-profiles/" + createProviderProfile(owner);

        mockMvc.perform(put(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, profileUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"kind":"MASTER","about":"Updated by owner"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.about").value("Updated by owner"));

        mockMvc.perform(get(profileUrl))
                .andExpect(jsonPath("$.about").value("Updated by owner"));
    }

    @Test
    void administrator_canUpdateAnotherUsersProfile() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String profileUrl = "/api/provider-profiles/" + createProviderProfile(owner);

        mockMvc.perform(put(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, profileUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"kind":"MASTER","about":"Edited by admin"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.about").value("Edited by admin"));

        mockMvc.perform(get(profileUrl))
                .andExpect(jsonPath("$.about").value("Edited by admin"))
                .andExpect(jsonPath("$.actorId").value(owner.id()));
    }

    @Test
    void nonOwner_cannotDeleteSomeoneElsesProfile() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        String profileUrl = "/api/provider-profiles/" + createProviderProfile(owner);

        mockMvc.perform(delete(profileUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .header(HttpHeaders.IF_MATCH, etagOf(stranger, profileUrl)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(profileUrl))
                .andExpect(status().isOk());
    }

    @Test
    void nonOwner_cannotRestoreSomeoneElsesProfileSnapshot() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        String profileUrl = "/api/provider-profiles/" + createProviderProfile(owner);

        mockMvc.perform(get(profileUrl + "/history").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        long snapshotId = snapshotIdOf(historyEntries(owner, profileUrl + "/history").getLast());

        mockMvc.perform(post(profileUrl + "/history/" + snapshotId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .header(HttpHeaders.IF_MATCH, etagOf(stranger, profileUrl)))
                .andExpect(status().isForbidden());
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

    /** Reads the resource's current ETag as the viewer, so an update, delete or restore can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
