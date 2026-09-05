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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Level 3 scenario: the full spectrum of {@code GET /api/users} filter/sort/pagination requests
 * against real data -- every field {@link org.ost.marketplace.ui.views.main.tabs.users.query.UserFilterMeta}/
 * {@code UserSortMeta} also exposes in the UI's own query bar. The first-ever registered user in a
 * clean DB becomes ADMIN ({@code UserService.register()}), so that key drives every privileged read.
 */
@Level3ScenarioTest
class UserPaginationScenarioTest extends AbstractRestApiScenarioTest {

    @Test
    void filterByName_returnsOnlySubstringMatches() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        registerNamed("Alice Plumber", "alice-plumber");
        registerNamed("Bob Electrician", "bob-electrician");
        registerNamed("Alicia Painter", "alicia-painter");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("name", "alic"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void filterByEmail_returnsOnlySubstringMatches() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        registerNamed("User One", "unique-domain-a");
        registerNamed("User Two", "unique-domain-b");
        registerNamed("User Three", "other-domain-c");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("email", "unique-domain"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void filterByRoles_returnsOnlyMatchingRole() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        registerNamed("Regular One", "regular-one");
        registerNamed("Regular Two", "regular-two");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].email").value(admin.email()));

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("roles", "USER"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void filterByCreatedAtRange_returnsOnlyWithinBounds() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        registerNamed("Old User", "old-user");
        String boundaryResponse = registerNamed("Boundary User", "boundary-user");
        Instant boundaryCreatedAt = Instant.parse(JsonScenarioUtils.extractStringField(
                fetchById(admin, JsonScenarioUtils.extractId(boundaryResponse)), "createdAt"));
        registerNamed("New User", "new-user");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("createdAtStart", boundaryCreatedAt.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void filterByStartIdEndId_returnsOnlyWithinRange() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        long idA = JsonScenarioUtils.extractId(registerNamed("User A", "user-a"));
        long idB = JsonScenarioUtils.extractId(registerNamed("User B", "user-b"));
        registerNamed("User C", "user-c");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("startId", String.valueOf(idA)).param("endId", String.valueOf(idB)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void combinedFilters_applyAsAnd() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        registerNamed("Alice Plumber", "alice-plumber-combo");
        registerNamed("Alice Electrician", "alice-electrician-combo");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("name", "alice").param("email", "plumber"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    void sortByEachField_bothDirections() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        registerNamed("Charlie", "charlie-sort");
        registerNamed("Alpha", "alpha-sort");
        registerNamed("Bravo", "bravo-sort");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("roles", "USER").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[2].name").value("Charlie"));

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("roles", "USER").param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Charlie"))
                .andExpect(jsonPath("$[2].name").value("Alpha"));

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("roles", "USER").param("sort", "email,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alpha"));

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("roles", "USER").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Charlie"))
                .andExpect(jsonPath("$[2].name").value("Bravo"));

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("sort", "id,asc").param("roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(admin.email()));

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("sort", "role,asc").param("roles", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void realDataVolume_pagesUsingCallersSavedPageSize() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        String settingsBody = """
                {"adsPageSize":20,"usersPageSize":10,"timelinePageSize":20,"version":0}""";
        mockMvc.perform(patch("/api/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(settingsBody))
                .andExpect(status().isOk());

        for (int i = 1; i <= 24; i++) {
            registerNamed("Volume User %02d".formatted(i), "volume-user-" + i);
        }

        // 24 volume users + 1 admin = 25 total.
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("page", "0").param("name", "Volume").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "24"))
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].name").value("Volume User 01"));

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("page", "2").param("name", "Volume").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("Volume User 21"));

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .param("page", "3").param("name", "Volume").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void nonAdminActor_listingUsers_getsRealForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser regular = registerUserAndIssueApiKey("Regular");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + regular.rawApiKey()))
                .andExpect(status().isForbidden());
    }

    private String registerNamed(String name, String emailPrefix) throws Exception {
        String email = emailPrefix + "-" + System.nanoTime() + "@example.com";
        String body = """
                {"name":"%s","email":"%s","password":"password123"}""".formatted(name, email);
        return mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String fetchById(RegisteredUser admin, long id) throws Exception {
        return mockMvc.perform(get("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
