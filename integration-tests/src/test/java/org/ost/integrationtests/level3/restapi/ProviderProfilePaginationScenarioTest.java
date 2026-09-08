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
                {"kind":"MASTER","about":"About First"}""";
        mockMvc.perform(put("/api/provider-profiles/" + first.id()).header("If-Match", "\"0\"")
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

        // createdAt has no unique tiebreaker for ProviderProfile (no unique sortable business field exists),
        // so rows created within the same timestamp tick can land on either side of a page boundary --
        // assert page sizes/total deterministically, and assert the full set is covered without gaps/dupes.
        java.util.List<String> collected = new java.util.ArrayList<>();
        for (int page = 0; page <= 3; page++) {
            String response = mockMvc.perform(get("/api/provider-profiles").param("page", String.valueOf(page)).param("size", "5").param("sort", "createdAt,asc"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Total-Count", "12"))
                    .andReturn().getResponse().getContentAsString();
            int expectedLength = switch (page) {
                case 0, 1 -> 5;
                case 2 -> 2;
                default -> 0;
            };
            com.jayway.jsonpath.DocumentContext json = com.jayway.jsonpath.JsonPath.parse(response);
            java.util.List<String> abouts = json.read("$[*].about");
            org.assertj.core.api.Assertions.assertThat(abouts).hasSize(expectedLength);
            collected.addAll(abouts);
        }
        java.util.List<String> expected = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) expected.add("About Provider%02d".formatted(i));
        org.assertj.core.api.Assertions.assertThat(collected).containsExactlyInAnyOrderElementsOf(expected);
    }
}
