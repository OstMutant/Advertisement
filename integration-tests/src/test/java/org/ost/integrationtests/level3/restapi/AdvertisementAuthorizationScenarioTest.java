package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenarios: real cross-user ownership checks and a real optimistic-locking conflict -- both need genuine persisted rows, not mocks. */
@Level3ScenarioTest
class AdvertisementAuthorizationScenarioTest extends AbstractRestApiScenarioTest {

    @Test
    void nonOwner_editingAnotherUsersAdvertisement_getsRealForbidden() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");

        String createBody = """
                {"title":"Owner's ad","description":"Desc","adKind":"OFFER"}""";
        String createResponse = mockMvc.perform(post("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long adId = JsonScenarioUtils.extractId(createResponse);

        String updateBody = """
                {"title":"Hijacked","description":"Desc","adKind":"OFFER","version":0}""";
        mockMvc.perform(put("/api/advertisements/" + adId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/advertisements/" + adId).param("version", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.rawApiKey()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staleVersion_onRealConcurrentEdit_getsRealConflict() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String createBody = """
                {"title":"Original","description":"Desc","adKind":"OFFER"}""";
        String createResponse = mockMvc.perform(post("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long adId = JsonScenarioUtils.extractId(createResponse);

        String firstEditBody = """
                {"title":"First edit","description":"Desc","adKind":"OFFER","version":0}""";
        mockMvc.perform(put("/api/advertisements/" + adId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(firstEditBody))
                .andExpect(status().isOk());

        // Second edit still carries version 0, but the row is now at version 1 after the first edit above.
        String staleEditBody = """
                {"title":"Second edit","description":"Desc","adKind":"OFFER","version":0}""";
        mockMvc.perform(put("/api/advertisements/" + adId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(staleEditBody))
                .andExpect(status().isConflict());
    }
}
