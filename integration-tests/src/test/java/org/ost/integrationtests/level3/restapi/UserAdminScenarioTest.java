package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: real role rules on user management over REST -- who may change a role, delete an account, and switch language. */
@Level3ScenarioTest
class UserAdminScenarioTest extends AbstractRestApiScenarioTest {

    private static final String TAXON_BODY = """
            {"type":"CATEGORY","translations":[{"locale":"en","name":"Electrical","description":"Electrical services"},{"locale":"uk","name":"Електрика","description":"Електромонтажні послуги"}]}""";

    @Autowired private JdbcClient jdbcClient;

    @Test
    void administrator_makesAUserModerator_whoThenCreatesTaxons_whileAPlainUserStillCannot() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser promoted = registerUserAndIssueApiKey("Promoted");
        RegisteredUser plain = registerUserAndIssueApiKey("Plain");

        mockMvc.perform(put("/api/users/" + promoted.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, versionOf(admin, promoted))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Promoted","role":"MODERATOR"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));

        mockMvc.perform(post("/api/taxons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + promoted.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(TAXON_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/taxons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plain.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(TAXON_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void administrator_changingTheirOwnRole_getsForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(put("/api/users/" + admin.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, versionOf(admin, admin))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Admin","role":"USER"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void plainUser_changingSomeoneElsesRole_getsForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser plain = registerUserAndIssueApiKey("Plain");
        RegisteredUser other = registerUserAndIssueApiKey("Other");

        mockMvc.perform(put("/api/users/" + other.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plain.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, versionOf(admin, other))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Other","role":"MODERATOR"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_deletingTheirOwnAccount_succeedsAndTheirKeyStopsWorking() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(delete("/api/users/" + owner.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void plainUser_deletingAnotherAccount_getsForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser plain = registerUserAndIssueApiKey("Plain");
        RegisteredUser other = registerUserAndIssueApiKey("Other");

        mockMvc.perform(delete("/api/users/" + other.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plain.rawApiKey()))
                .andExpect(status().isForbidden());
    }

    @Test
    void changingLanguage_isStoredForTheCaller_andAnUnsupportedLocaleIsRejected() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Speaker");

        mockMvc.perform(put("/api/users/me/locale")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"locale":"uk"}"""))
                .andExpect(status().isNoContent());

        String stored = jdbcClient.sql("SELECT locale FROM user_preferences WHERE actor_id = :id")
                .param("id", user.id()).query(String.class).single();
        assertEquals("uk", stored);

        mockMvc.perform(put("/api/users/me/locale")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"locale":"de"}"""))
                .andExpect(status().isBadRequest());
    }

    /** Reads the target's current ETag as the viewer, so a PUT can send it back in If-Match. */
    private String versionOf(RegisteredUser viewer, RegisteredUser target) throws Exception {
        return mockMvc.perform(get("/api/users/" + target.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer.rawApiKey()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }
}
