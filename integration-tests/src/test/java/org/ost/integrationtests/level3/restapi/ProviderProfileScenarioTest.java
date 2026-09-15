package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: create provider profile referencing real taxon category/city ids -- verifies categoryNames/cityName resolve from real taxon data, not fixture stand-ins. */
@Level3ScenarioTest
class ProviderProfileScenarioTest extends AbstractRestApiScenarioTest {

    @Test
    void createProviderProfile_resolvesRealCategoryAndCityNames() throws Exception {
        // The first-ever registered user becomes ADMIN (UserService.register()), so this key can create taxons.
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        String categoryBody = """
                {"type":"CATEGORY","translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"},{"locale":"uk","name":"Сантехніка","description":"Сантехнічні послуги"}]}""";
        String categoryResponse = mockMvc.perform(post("/api/taxons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(categoryBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long categoryId = JsonScenarioUtils.extractId(categoryResponse);

        String cityBody = """
                {"type":"CITY","translations":[{"locale":"en","name":"Kyiv","description":"Capital of Ukraine"},{"locale":"uk","name":"Київ","description":"Столиця України"}]}""";
        String cityResponse = mockMvc.perform(post("/api/taxons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(cityBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long cityId = JsonScenarioUtils.extractId(cityResponse);

        RegisteredUser provider = registerUserAndIssueApiKey("Provider");
        String profileBody = """
                {"kind":"MASTER","about":"Experienced plumber","categoryIds":[%d],"cityTaxonId":%d}""".formatted(categoryId, cityId);

        mockMvc.perform(post("/api/provider-profiles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(profileBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryNames[0]").value("Plumbing"))
                .andExpect(jsonPath("$.cityName").value("Kyiv"));
    }
}
