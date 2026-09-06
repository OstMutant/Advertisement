package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Level 3 scenario: the full spectrum of {@code GET /api/provider-profiles} filter/sort/pagination
 * requests against real data -- every field {@link org.ost.marketplace.ui.views.main.tabs.providers.query.ProviderProfileFilterMeta}/
 * {@code ProviderProfileSortMeta} also exposes in the UI's own query bar. One profile per actor
 * (real unique index on {@code actor_id}), so each data point is its own registered user.
 */
@Level3ScenarioTest
class ProviderProfilePaginationScenarioTest extends AbstractRestApiScenarioTest {

    private long createCategory(RegisteredUser admin, String name) throws Exception {
        String body = """
                {"type":"CATEGORY","translations":[{"locale":"en","name":"%s","description":"%s services"},{"locale":"uk","name":"%s","description":"%s послуги"}]}"""
                .formatted(name, name, name, name);
        String response = mockMvc.perform(post("/api/taxons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(response);
    }

    private long createCity(RegisteredUser admin, String name) throws Exception {
        String body = """
                {"type":"CITY","translations":[{"locale":"en","name":"%s","description":"City of %s"},{"locale":"uk","name":"%s","description":"Місто %s"}]}"""
                .formatted(name, name, name, name);
        String response = mockMvc.perform(post("/api/taxons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(response);
    }

    private record CreatedProvider(long id, RegisteredUser owner, String responseJson) {
    }

    private CreatedProvider createProvider(String namePrefix, String kind, Long categoryId, Long cityTaxonId) throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey(namePrefix);
        String categoryPart = categoryId != null ? "\"categoryIds\":[%d],".formatted(categoryId) : "";
        String cityPart = cityTaxonId != null ? "\"cityTaxonId\":%d,".formatted(cityTaxonId) : "";
        String body = """
                {"kind":"%s",%s%s"about":"About %s"}""".formatted(kind, categoryPart, cityPart, namePrefix);
        String response = mockMvc.perform(post("/api/provider-profiles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new CreatedProvider(JsonScenarioUtils.extractId(response), owner, response);
    }

    @Test
    void filterByKinds_returnsOnlyMatchingKind() throws Exception {
        // Support1 registers first -- the first-ever user in a clean DB becomes ADMIN, required for kind=SUPPORT.
        createProvider("Support1", "SUPPORT", null, null);
        createProvider("Master1", "MASTER", null, null);
        createProvider("Shop1", "SHOP", null, null);

        mockMvc.perform(get("/api/provider-profiles").param("kinds", "SHOP"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].kind").value("SHOP"));

        mockMvc.perform(get("/api/provider-profiles").param("kinds", "MASTER,SUPPORT"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void filterByCreatedAtRange_returnsOnlyWithinBounds() throws Exception {
        createProvider("Old", "MASTER", null, null);
        CreatedProvider boundary = createProvider("Boundary", "MASTER", null, null);
        Instant boundaryCreatedAt = Instant.parse(JsonScenarioUtils.extractStringField(boundary.responseJson(), "createdAt"));
        createProvider("New", "MASTER", null, null);

        mockMvc.perform(get("/api/provider-profiles")
                        .param("createdAtStart", boundaryCreatedAt.toString())
                        .param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void filterByCategoryIdsAndCityTaxonId_returnsOnlyMatching() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long categoryId = createCategory(admin, "Plumbing");
        long otherCategoryId = createCategory(admin, "Electrical");
        long cityId = createCity(admin, "Kyiv");

        createProvider("PlumberInKyiv", "MASTER", categoryId, cityId);
        createProvider("ElectricianElsewhere", "MASTER", otherCategoryId, null);
        createProvider("Uncategorized", "MASTER", null, null);

        mockMvc.perform(get("/api/provider-profiles").param("categoryIds", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));

        mockMvc.perform(get("/api/provider-profiles").param("cityTaxonId", String.valueOf(cityId)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    void combinedFilters_applyAsAnd() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long cityId = createCity(admin, "Lviv");

        createProvider("MasterInLviv", "MASTER", null, cityId);
        createProvider("ShopInLviv", "SHOP", null, cityId);
        createProvider("MasterElsewhere", "MASTER", null, null);

        mockMvc.perform(get("/api/provider-profiles").param("kinds", "MASTER").param("cityTaxonId", String.valueOf(cityId)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    void sortByEachField_bothDirections() throws Exception {
        CreatedProvider first = createProvider("First", "MASTER", null, null);
        createProvider("Second", "MASTER", null, null);
        createProvider("Third", "MASTER", null, null);

        mockMvc.perform(get("/api/provider-profiles").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].about").value("About First"))
                .andExpect(jsonPath("$[2].about").value("About Third"));

        mockMvc.perform(get("/api/provider-profiles").param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].about").value("About Third"))
                .andExpect(jsonPath("$[2].about").value("About First"));

        // Updating "First" last (via its own owner's bearer key -- self-service) must move it to the end of updatedAt,asc.
        String updateBody = """
                {"kind":"MASTER","about":"About First","version":0}""";
        mockMvc.perform(put("/api/provider-profiles/" + first.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.owner().rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/provider-profiles").param("sort", "updatedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].about").value("About First"));
    }

    @Test
    void realDataVolume_pagesAndSorts() throws Exception {
        for (int i = 1; i <= 12; i++) {
            createProvider("Provider%02d".formatted(i), "MASTER", null, null);
        }

        mockMvc.perform(get("/api/provider-profiles").param("page", "0").param("size", "5").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "12"))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].about").value("About Provider01"));

        mockMvc.perform(get("/api/provider-profiles").param("page", "1").param("size", "5").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].about").value("About Provider06"));

        mockMvc.perform(get("/api/provider-profiles").param("page", "2").param("size", "5").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].about").value("About Provider11"));

        mockMvc.perform(get("/api/provider-profiles").param("page", "3").param("size", "5").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "12"))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
