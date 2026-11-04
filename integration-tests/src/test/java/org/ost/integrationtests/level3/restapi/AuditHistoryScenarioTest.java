package org.ost.integrationtests.level3.restapi;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: who may read per-entity history, the global timeline, and a snapshot, and restoring one through a normal update. */
@Level3ScenarioTest
class AuditHistoryScenarioTest extends AbstractRestApiScenarioTest {

    @Test
    void anonymous_onAdvertisementHistory_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/advertisements/1/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    @Test
    void administrator_readsTimeline_regularUserIsForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser plain = registerUserAndIssueApiKey("Plain");

        mockMvc.perform(get("/api/audit/timeline").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"));

        mockMvc.perform(get("/api/audit/timeline").header(HttpHeaders.AUTHORIZATION, "Bearer " + plain.rawApiKey()))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_seesOwnHistory_andReadsSnapshot_thenRestoresWithPut() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long adId = createAdvertisement(owner);

        String historyResponse = mockMvc.perform(get("/api/advertisements/" + adId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].snapshotId").exists())
                .andReturn().getResponse().getContentAsString();
        long snapshotId = ((Number) JsonPath.read(historyResponse, "$[0].snapshotId")).longValue();

        String snapshotResponse = mockMvc.perform(get("/api/advertisements/" + adId + "/history/" + snapshotId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.snapshot.title").value("Original"))
                .andReturn().getResponse().getContentAsString();

        String etag = mockMvc.perform(get("/api/advertisements/" + adId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        String title = JsonPath.read(snapshotResponse, "$.snapshot.title");
        String description = JsonPath.read(snapshotResponse, "$.snapshot.description");
        String adKind = JsonPath.read(snapshotResponse, "$.snapshot.adKind");
        String restoreBody = """
                {"title":"%s","description":"%s","adKind":"%s"}""".formatted(title, description, adKind);

        mockMvc.perform(put("/api/advertisements/" + adId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON).content(restoreBody))
                .andExpect(status().isOk());
    }

    @Test
    void otherUser_cannotReadSnapshotOfForeignAdvertisement() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long adId = createAdvertisement(owner);

        String historyResponse = mockMvc.perform(get("/api/advertisements/" + adId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long snapshotId = ((Number) JsonPath.read(historyResponse, "$[0].snapshotId")).longValue();

        mockMvc.perform(get("/api/advertisements/" + adId + "/history/" + snapshotId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.rawApiKey()))
                .andExpect(status().isForbidden());
    }

    /** Creates one advertisement titled "Original" as the given owner and returns its id. */
    private long createAdvertisement(RegisteredUser owner) throws Exception {
        String createBody = """
                {"title":"Original","description":"Desc","adKind":"OFFER"}""";
        String createResponse = mockMvc.perform(post("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(createResponse);
    }
}
