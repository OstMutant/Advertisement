package org.ost.integrationtests.level3.restapi;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: the caller's own paginated-list settings over REST -- read with ETag, save with optimistic locking, per-user history, and snapshot restore. */
@Level3ScenarioTest
class UserSettingsScenarioTest extends AbstractRestApiScenarioTest {

    private static final String SETTINGS_URL = "/api/users/me/settings";
    private static final String HISTORY_URL = SETTINGS_URL + "/history";

    @Test
    void member_readsOwnSettingsWithETag() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");

        mockMvc.perform(get(SETTINGS_URL).header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.adsPageSize").exists())
                .andExpect(jsonPath("$.usersPageSize").exists())
                .andExpect(jsonPath("$.timelinePageSize").exists())
                .andExpect(jsonPath("$.providerProfilesPageSize").exists());
    }

    @Test
    void anonymous_onSettings_isUnauthorized() throws Exception {
        mockMvc.perform(get(SETTINGS_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void savedSettings_doNotLeakToAnotherUser() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        RegisteredUser other = registerUserAndIssueApiKey("Other");

        saveSettings(member, 30);

        mockMvc.perform(get(SETTINGS_URL).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adsPageSize").value(not(30)));
    }

    @Test
    void member_readsHistoryAfterTwoSaves() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        twoSaves(member);

        assertThat(historyEntries(member, HISTORY_URL)).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void other_seesOnlyTheirOwnRegistrationEntry() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        RegisteredUser other = registerUserAndIssueApiKey("Other");
        twoSaves(member);

        mockMvc.perform(get(HISTORY_URL).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].actionType").value("CREATED"))
                .andExpect(jsonPath("$[0].actorId").value(other.id()));
    }

    @Test
    void anonymous_onSettingsHistory_isUnauthorized() throws Exception {
        mockMvc.perform(get(HISTORY_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void member_readsOldestHistorySnapshot() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        twoSaves(member);

        mockMvc.perform(get(HISTORY_URL + "/" + oldestSnapshotId(member))
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.snapshot.adsPageSize").exists());
    }

    @Test
    void other_readingMembersSnapshot_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        RegisteredUser other = registerUserAndIssueApiKey("Other");
        twoSaves(member);

        mockMvc.perform(get(HISTORY_URL + "/" + oldestSnapshotId(member))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_onSettingsHistorySnapshot_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        twoSaves(member);

        mockMvc.perform(get(HISTORY_URL + "/" + oldestSnapshotId(member)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void member_unknownSnapshot_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        twoSaves(member);

        mockMvc.perform(get(HISTORY_URL + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void member_restoresOldestSnapshot() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        twoSaves(member);
        long snapshotId = oldestSnapshotId(member);
        String snapshotJson = mockMvc.perform(get(HISTORY_URL + "/" + snapshotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer snapshotAdsPageSize = JsonPath.read(snapshotJson, "$.snapshot.adsPageSize");

        mockMvc.perform(post(HISTORY_URL + "/" + snapshotId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .header(HttpHeaders.IF_MATCH, etagOf(member, SETTINGS_URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adsPageSize").value(snapshotAdsPageSize))
                .andExpect(jsonPath("$.adsPageSize").value(not(40)));
    }

    @Test
    void restoreWithStaleVersion_isPreconditionFailed() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        twoSaves(member);

        mockMvc.perform(post(HISTORY_URL + "/" + oldestSnapshotId(member) + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void restoreWithoutIfMatch_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        twoSaves(member);

        mockMvc.perform(post(HISTORY_URL + "/" + oldestSnapshotId(member) + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void other_restoringMembersSnapshot_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        RegisteredUser other = registerUserAndIssueApiKey("Other");
        twoSaves(member);

        mockMvc.perform(post(HISTORY_URL + "/" + oldestSnapshotId(member) + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .header(HttpHeaders.IF_MATCH, etagOf(other, SETTINGS_URL)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_restoringSettingsSnapshot_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser member = registerUserAndIssueApiKey("Member");
        twoSaves(member);

        mockMvc.perform(post(HISTORY_URL + "/" + oldestSnapshotId(member) + "/restore")
                        .header(HttpHeaders.IF_MATCH, etagOf(member, SETTINGS_URL)))
                .andExpect(status().isUnauthorized());
    }

    /** Saves all four page sizes to the same value, fetching the current ETag first so the save passes If-Match. */
    private void saveSettings(RegisteredUser user, int pageSize) throws Exception {
        mockMvc.perform(patch(SETTINGS_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(HttpHeaders.IF_MATCH, etagOf(user, SETTINGS_URL))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"adsPageSize":%1$d,"usersPageSize":%1$d,"timelinePageSize":%1$d,"providerProfilesPageSize":%1$d}""".formatted(pageSize)))
                .andExpect(status().isOk());
    }

    /** Saves the settings twice with different values, so the user's settings history holds at least two entries. */
    private void twoSaves(RegisteredUser user) throws Exception {
        saveSettings(user, 30);
        saveSettings(user, 40);
    }

    /** Returns the id of the oldest settings history entry of the given user. */
    private long oldestSnapshotId(RegisteredUser user) throws Exception {
        return snapshotIdOf(historyEntries(user, HISTORY_URL).getLast());
    }

    /** Returns the settings history list as the viewer sees it, newest entry first. */
    private List<Map<String, Object>> historyEntries(RegisteredUser viewer, String historyUrl) throws Exception {
        String json = mockMvc.perform(get(historyUrl).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> entries = JsonPath.read(json, "$");
        return entries;
    }

    private static long snapshotIdOf(Map<String, Object> historyEntry) {
        return ((Number) historyEntry.get("snapshotId")).longValue();
    }

    /** Reads the resource's current ETag as the viewer, so a save or restore can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
