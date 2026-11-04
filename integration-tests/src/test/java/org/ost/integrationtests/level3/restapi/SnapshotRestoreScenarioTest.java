package org.ost.integrationtests.level3.restapi;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: restoring advertisements, taxons and users to a recorded snapshot through their REST restore endpoints. */
@Level3ScenarioTest
class SnapshotRestoreScenarioTest extends AbstractRestApiScenarioTest {

    private static final String TAXON_BODY = """
            {"type":"CATEGORY","translations":[{"locale":"en","name":"Electrical","description":"Electrical services"},{"locale":"uk","name":"Електрика","description":"Електромонтажні послуги"}]}""";

    @Test
    void advertisement_restoredToItsFirstSnapshot() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = "/api/advertisements/" + createAdvertisement(owner, "Original");
        mockMvc.perform(put(adUrl).header("If-Match", "\"0\"")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"Changed","description":"Desc","adKind":"OFFER"}"""))
                .andExpect(status().isOk());

        List<Map<String, Object>> before = historyEntries(owner, adUrl + "/history");
        long creationSnapshotId = snapshotIdOf(before.getLast());
        String etag = etagOf(owner, adUrl);

        mockMvc.perform(post(adUrl + "/history/" + creationSnapshotId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, etag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original"));

        mockMvc.perform(get(adUrl)).andExpect(jsonPath("$.title").value("Original"));
        assertThat(historyEntries(owner, adUrl + "/history")).hasSize(before.size() + 1);
    }

    @Test
    void advertisement_restoreWithStaleVersion_isPreconditionFailed() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = "/api/advertisements/" + createAdvertisement(owner, "Original");
        mockMvc.perform(put(adUrl).header("If-Match", "\"0\"")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"Changed","description":"Desc","adKind":"OFFER"}"""))
                .andExpect(status().isOk());
        long creationSnapshotId = snapshotIdOf(historyEntries(owner, adUrl + "/history").getLast());

        // The row is at version 1 after the edit above, so "0" is stale.
        mockMvc.perform(post(adUrl + "/history/" + creationSnapshotId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void restore_someoneElsesSnapshot_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        String adUrl = "/api/advertisements/" + createAdvertisement(owner, "Original");
        long creationSnapshotId = snapshotIdOf(historyEntries(owner, adUrl + "/history").getLast());

        mockMvc.perform(post(adUrl + "/history/" + creationSnapshotId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isForbidden());
    }

    @Test
    void taxon_deletedThenRestored() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        String createResponse = mockMvc.perform(post("/api/taxons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(TAXON_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long taxonId = JsonScenarioUtils.extractId(createResponse);
        String taxonUrl = "/api/taxons/" + taxonId;

        mockMvc.perform(delete(taxonUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, taxonUrl)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(taxonUrl + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taxonId));

        mockMvc.perform(post(taxonUrl + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/taxons/999999/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey()))
                .andExpect(status().isNotFound());
    }

    @Test
    void user_restoredToPreviousName() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser target = registerUserAndIssueApiKey("Target");
        String userUrl = "/api/users/" + target.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Renamed","role":"USER"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));

        long firstSnapshotId = snapshotIdOf(historyEntries(admin, userUrl + "/history").getLast());

        mockMvc.perform(post(userUrl + "/history/" + firstSnapshotId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Target"));

        mockMvc.perform(get(userUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey()))
                .andExpect(jsonPath("$.name").value("Target"));
    }

    @Test
    void getUserHistorySnapshot_adminReadsOldestSnapshot() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser target = registerUserAndIssueApiKey("Target");
        String userUrl = "/api/users/" + target.id();
        renameUser(admin, userUrl, "Renamed");

        long oldest = snapshotIdOf(historyEntries(admin, userUrl + "/history").getLast());
        mockMvc.perform(get(userUrl + "/history/" + oldest).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.snapshot.name").value("Target"));
    }

    @Test
    void getUserHistorySnapshot_moderatorReadsOldestSnapshot() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser target = registerUserAndIssueApiKey("Target");
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        String userUrl = "/api/users/" + target.id();
        promoteToModerator(admin, moderator, "Moderator");
        renameUser(admin, userUrl, "Renamed");

        long oldest = snapshotIdOf(historyEntries(admin, userUrl + "/history").getLast());
        mockMvc.perform(get(userUrl + "/history/" + oldest).header(HttpHeaders.AUTHORIZATION, "Bearer " + moderator.rawApiKey()))
                .andExpect(status().isOk());
    }

    @Test
    void getUserHistorySnapshot_targetHimself_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser target = registerUserAndIssueApiKey("Target");
        String userUrl = "/api/users/" + target.id();
        renameUser(admin, userUrl, "Renamed");

        long adminRenameSnapshotId = snapshotIdOf(historyEntries(admin, userUrl + "/history").getFirst());
        mockMvc.perform(get(userUrl + "/history/" + adminRenameSnapshotId).header(HttpHeaders.AUTHORIZATION, "Bearer " + target.rawApiKey()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserHistorySnapshot_otherPlainUser_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser target = registerUserAndIssueApiKey("Target");
        RegisteredUser other = registerUserAndIssueApiKey("Other");
        String userUrl = "/api/users/" + target.id();
        renameUser(admin, userUrl, "Renamed");

        long oldest = snapshotIdOf(historyEntries(admin, userUrl + "/history").getLast());
        mockMvc.perform(get(userUrl + "/history/" + oldest).header(HttpHeaders.AUTHORIZATION, "Bearer " + other.rawApiKey()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserHistorySnapshot_anonymous_isUnauthorized() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser target = registerUserAndIssueApiKey("Target");
        String userUrl = "/api/users/" + target.id();
        renameUser(admin, userUrl, "Renamed");

        long oldest = snapshotIdOf(historyEntries(admin, userUrl + "/history").getLast());
        mockMvc.perform(get(userUrl + "/history/" + oldest))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserHistorySnapshot_unknownSnapshot_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser target = registerUserAndIssueApiKey("Target");
        String userUrl = "/api/users/" + target.id();
        renameUser(admin, userUrl, "Renamed");

        mockMvc.perform(get(userUrl + "/history/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey()))
                .andExpect(status().isNotFound());
    }

    /** Renames the target user as the administrator, so the target has a previous name to read from history. */
    private void renameUser(RegisteredUser admin, String userUrl, String name) throws Exception {
        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"%s","role":"USER"}""".formatted(name)))
                .andExpect(status().isOk());
    }

    /** Promotes the given user to MODERATOR as the administrator, keeping the name the user registered with. */
    private void promoteToModerator(RegisteredUser admin, RegisteredUser user, String name) throws Exception {
        String userUrl = "/api/users/" + user.id();
        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"%s","role":"MODERATOR"}""".formatted(name)))
                .andExpect(status().isOk());
    }

    /** Creates one advertisement with the given title as the owner and returns its id. */
    private long createAdvertisement(RegisteredUser owner, String title) throws Exception {
        String createBody = """
                {"title":"%s","description":"Desc","adKind":"OFFER"}""".formatted(title);
        String createResponse = mockMvc.perform(post("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(createResponse);
    }

    /** Returns the history list as the viewer sees it, newest entry first. */
    private List<Map<String, Object>> historyEntries(RegisteredUser viewer, String historyUrl) throws Exception {
        String json = mockMvc.perform(get(historyUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer.rawApiKey()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> entries = JsonPath.read(json, "$");
        return entries;
    }

    private static long snapshotIdOf(Map<String, Object> historyEntry) {
        return ((Number) historyEntry.get("snapshotId")).longValue();
    }

    /** Reads the resource's current ETag as the viewer, so a restore or update can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer.rawApiKey()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }
}
