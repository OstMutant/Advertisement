package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: real privilege check on taxon writes, plus real locale fallback to English for an unrequested locale. */
@Level3ScenarioTest
class TaxonAdminScenarioTest extends AbstractRestApiScenarioTest {

    @Test
    void nonAdminActor_creatingTaxon_getsRealForbidden() throws Exception {
        // The first-ever registered user becomes ADMIN; this second one stays a plain USER.
        registerUserAndIssueApiKey("Admin");
        RegisteredUser regularUser = registerUserAndIssueApiKey("Regular");

        String body = """
                {"type":"CATEGORY","translations":[{"locale":"en","name":"Electrical","description":"Electrical services"},{"locale":"uk","name":"Електрика","description":"Електромонтажні послуги"}]}""";

        mockMvc.perform(post("/api/taxons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + regularUser.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void unrequestedLocale_fallsBackToRealEnglishTranslation() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        String body = """
                {"type":"CATEGORY","translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"},{"locale":"uk","name":"Сантехніка","description":"Сантехнічні послуги"}]}""";
        String createResponse = mockMvc.perform(post("/api/taxons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long taxonId = JsonScenarioUtils.extractId(createResponse);

        // "de" is not a supportedLocale (only en/uk are) -- must fall back to the default locale (English).
        mockMvc.perform(get("/api/taxons/" + taxonId).param("locale", "de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Plumbing"));
    }
}
