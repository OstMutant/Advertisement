package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.ost.integrationtests.support.TimestampObservations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: create user -> issue API key -> create several advertisements, end to end, no mocks. */
@Level3ScenarioTest
class UserApiKeyAdvertisementScenarioTest extends AbstractRestApiScenarioTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void createUser_issueApiKey_createSeveralAdvertisements() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Scenario User");

        Map<Long, String> titleById = new LinkedHashMap<>();
        for (int i = 1; i <= 3; i++) {
            String adBody = """
                    {"title":"Ad %d","description":"Description %d","adKind":"OFFER"}""".formatted(i, i);
            String response = mockMvc.perform(post("/api/advertisements")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                            .contentType(MediaType.APPLICATION_JSON).content(adBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.createdBy").value(user.id()))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andReturn().getResponse().getContentAsString();
            titleById.put(JsonScenarioUtils.extractId(response), "Ad " + i);
        }

        Map<Long, Instant> createdAtById = TimestampObservations.read(
                jdbcClient, "advertisement", "created_at", List.copyOf(titleById.keySet()));
        List<String> expectedDesc = titleById.keySet().stream()
                .sorted(Comparator.comparing(createdAtById::get).reversed())
                .map(titleById::get)
                .toList();

        mockMvc.perform(get("/api/advertisements").param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$[0].title").value(expectedDesc.get(0)))
                .andExpect(jsonPath("$[2].title").value(expectedDesc.get(2)));
    }
}
