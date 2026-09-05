package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: page/size/sort behavior against a real result set, page size resolved from the caller's real saved settings -- a mocked service returning a canned list can't actually prove either. */
@Level3ScenarioTest
class AdvertisementPaginationScenarioTest extends AbstractRestApiScenarioTest {

    @Test
    void realDataVolume_pagesAndSortsUsingCallersSavedPageSize() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Seller");

        String settingsBody = """
                {"adsPageSize":10,"usersPageSize":20,"timelinePageSize":20,"version":0}""";
        mockMvc.perform(patch("/api/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(settingsBody))
                .andExpect(status().isOk());

        for (int i = 1; i <= 25; i++) {
            String body = """
                    {"title":"Ad %02d","description":"Description","adKind":"OFFER"}""".formatted(i);
            mockMvc.perform(post("/api/advertisements")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }

        // "size=999" in the URL must have no effect -- the real page size (10) comes from the saved setting above.
        mockMvc.perform(get("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                        .param("page", "0").param("size", "999").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "25"))
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].title").value("Ad 01"))
                .andExpect(jsonPath("$[9].title").value("Ad 10"));

        mockMvc.perform(get("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                        .param("page", "2").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].title").value("Ad 21"))
                .andExpect(jsonPath("$[4].title").value("Ad 25"));

        mockMvc.perform(get("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                        .param("page", "0").param("sort", "title,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Ad 25"));
    }
}
