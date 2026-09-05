package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Level 3 scenario: the full spectrum of {@code GET /api/advertisements} filter/sort/pagination
 * requests against real data -- every field {@link org.ost.marketplace.ui.views.main.tabs.advertisements.query.AdvertisementFilterMeta}/
 * {@code AdvertisementSortMeta} also exposes in the UI's own query bar, individually and combined.
 */
@Level3ScenarioTest
class AdvertisementPaginationScenarioTest extends AbstractRestApiScenarioTest {

    private RegisteredUser createSeller() throws Exception {
        return registerUserAndIssueApiKey("Seller");
    }

    private String createAd(RegisteredUser seller, String title, String adKind, Long categoryId, Long cityTaxonId) throws Exception {
        String categoryPart = categoryId != null ? "\"categoryIds\":[%d],".formatted(categoryId) : "";
        String cityPart = cityTaxonId != null ? "\"cityTaxonId\":%d,".formatted(cityTaxonId) : "";
        String body = """
                {"title":"%s","description":"Description",%s%s"adKind":"%s"}""".formatted(title, categoryPart, cityPart, adKind);
        return mockMvc.perform(post("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + seller.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void filterByTitle_returnsOnlySubstringMatches() throws Exception {
        RegisteredUser seller = createSeller();
        createAd(seller, "Plumbing services", "OFFER", null, null);
        createAd(seller, "Electrical services", "OFFER", null, null);
        createAd(seller, "Home plumbing repair", "OFFER", null, null);

        mockMvc.perform(get("/api/advertisements").param("title", "plumb"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[*].title", org.hamcrest.Matchers.containsInAnyOrder("Plumbing services", "Home plumbing repair")));
    }

    @Test
    void filterByAdKind_returnsOnlyMatchingKind() throws Exception {
        RegisteredUser seller = createSeller();
        createAd(seller, "Offer ad", "OFFER", null, null);
        createAd(seller, "Request ad", "REQUEST", null, null);
        createAd(seller, "Product ad", "PRODUCT", null, null);

        mockMvc.perform(get("/api/advertisements").param("adKinds", "REQUEST"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].title").value("Request ad"));

        mockMvc.perform(get("/api/advertisements").param("adKinds", "OFFER,PRODUCT"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void filterByCreatedAtRange_returnsOnlyWithinBounds() throws Exception {
        RegisteredUser seller = createSeller();
        createAd(seller, "Old ad", "OFFER", null, null);
        String boundaryResponse = createAd(seller, "Boundary ad", "OFFER", null, null);
        Instant boundaryCreatedAt = Instant.parse(JsonScenarioUtils.extractStringField(boundaryResponse, "createdAt"));
        createAd(seller, "New ad", "OFFER", null, null);

        // createdAtStart is inclusive (>=) -- boundary and everything created after it must be included, "Old ad" excluded.
        mockMvc.perform(get("/api/advertisements")
                        .param("createdAtStart", boundaryCreatedAt.toString())
                        .param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].title").value("Boundary ad"))
                .andExpect(jsonPath("$[1].title").value("New ad"));
    }

    @Test
    void filterByCategoryIdsAndCityTaxonId_returnsOnlyMatching() throws Exception {
        RegisteredUser admin = createSeller();
        long categoryId = createCategory(admin, "Plumbing");
        long otherCategoryId = createCategory(admin, "Electrical");
        long cityId = createCity(admin, "Kyiv");

        createAd(admin, "Plumbing in Kyiv", "OFFER", categoryId, cityId);
        createAd(admin, "Electrical anywhere", "OFFER", otherCategoryId, null);
        createAd(admin, "Uncategorized", "OFFER", null, null);

        mockMvc.perform(get("/api/advertisements").param("categoryIds", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].title").value("Plumbing in Kyiv"));

        mockMvc.perform(get("/api/advertisements").param("cityTaxonId", String.valueOf(cityId)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].title").value("Plumbing in Kyiv"));
    }

    @Test
    void combinedFilters_applyAsAnd() throws Exception {
        RegisteredUser seller = createSeller();
        createAd(seller, "Plumbing offer", "OFFER", null, null);
        createAd(seller, "Plumbing request", "REQUEST", null, null);
        createAd(seller, "Electrical offer", "OFFER", null, null);

        mockMvc.perform(get("/api/advertisements").param("title", "plumbing").param("adKinds", "OFFER"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].title").value("Plumbing offer"));
    }

    @Test
    void sortByEachField_bothDirections() throws Exception {
        RegisteredUser seller = createSeller();
        long idA = JsonScenarioUtils.extractId(createAd(seller, "Charlie", "OFFER", null, null));
        createAd(seller, "Alpha", "OFFER", null, null);
        createAd(seller, "Bravo", "OFFER", null, null);

        mockMvc.perform(get("/api/advertisements").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Alpha"))
                .andExpect(jsonPath("$[2].title").value("Charlie"));

        mockMvc.perform(get("/api/advertisements").param("sort", "title,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Charlie"))
                .andExpect(jsonPath("$[2].title").value("Alpha"));

        mockMvc.perform(get("/api/advertisements").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Charlie"))
                .andExpect(jsonPath("$[2].title").value("Bravo"));

        mockMvc.perform(get("/api/advertisements").param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Bravo"))
                .andExpect(jsonPath("$[2].title").value("Charlie"));

        // Updating "Charlie" (the first-created ad) last must move it to the end of an updatedAt,asc sort.
        String updateBody = """
                {"title":"Charlie","description":"Description","adKind":"OFFER","version":0}""";
        mockMvc.perform(put("/api/advertisements/" + idA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + seller.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/advertisements").param("sort", "updatedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Charlie"));
    }

    @Test
    void realDataVolume_pagesUsingCallersSavedPageSize() throws Exception {
        RegisteredUser user = createSeller();

        String settingsBody = """
                {"adsPageSize":10,"usersPageSize":20,"timelinePageSize":20,"version":0}""";
        mockMvc.perform(patch("/api/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(settingsBody))
                .andExpect(status().isOk());

        for (int i = 1; i <= 25; i++) {
            createAd(user, "Ad %02d".formatted(i), "OFFER", null, null);
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

        // Middle page.
        mockMvc.perform(get("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                        .param("page", "1").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].title").value("Ad 11"))
                .andExpect(jsonPath("$[9].title").value("Ad 20"));

        // Last, partial page.
        mockMvc.perform(get("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                        .param("page", "2").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].title").value("Ad 21"))
                .andExpect(jsonPath("$[4].title").value("Ad 25"));

        // Beyond the last page -- empty, not an error.
        mockMvc.perform(get("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                        .param("page", "3").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "25"))
                .andExpect(jsonPath("$.length()").value(0));
    }

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
}
