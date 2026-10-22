package org.ost.integrationtests.level3.restapi;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 matrix: every user REST operation across the anonymous, owner, stranger, moderator and administrator roles, with its validation, precondition and rate-limit edges. */
@Level3ScenarioTest
class UserMatrixScenarioTest extends AbstractRestApiScenarioTest {

    private static final String USERS = "/api/users";
    private static final String UNKNOWN_ID = "999999";
    private static final String UNKNOWN_USER = USERS + "/" + UNKNOWN_ID;
    private static final String LOCAL_IP = "127.0.0.1";
    private static final String REGISTRATION_IP = "10.3.0.1";
    private static final String VALID_EMAIL = "jane@example.com";
    private static final String VALID_PASSWORD = "password123";
    private static final String STALE_IF_MATCH = "\"0\"";
    private static final String UPDATED_BODY = """
            {"name":"New Name","role":"USER"}""";

    // ── register ─────────────────────────────────────────────────────────

    @Test
    void register_blankName_isBadRequest() throws Exception {
        registerFrom(signUpBody("", VALID_EMAIL, VALID_PASSWORD), LOCAL_IP)
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_nameLongerThan100_isBadRequest() throws Exception {
        registerFrom(signUpBody("a".repeat(101), VALID_EMAIL, VALID_PASSWORD), LOCAL_IP)
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_malformedEmail_isBadRequest() throws Exception {
        registerFrom(signUpBody("Jane", "not-an-email", VALID_PASSWORD), LOCAL_IP)
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_emailLongerThan254_isBadRequest() throws Exception {
        String longEmail = "a".repeat(255 - "@example.com".length()) + "@example.com";
        registerFrom(signUpBody("Jane", longEmail, VALID_PASSWORD), LOCAL_IP)
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordShorterThan6_isBadRequest() throws Exception {
        registerFrom(signUpBody("Jane", VALID_EMAIL, "passw"), LOCAL_IP)
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordLongerThan255_isBadRequest() throws Exception {
        registerFrom(signUpBody("Jane", VALID_EMAIL, "p".repeat(256)), LOCAL_IP)
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_validDetails_isCreated() throws Exception {
        registerFrom(signUpBody("Jane", VALID_EMAIL, VALID_PASSWORD), LOCAL_IP)
                .andExpect(status().isCreated());
    }

    @Test
    void register_sameEmailTwice_isConflict() throws Exception {
        registerFrom(signUpBody("Jane", VALID_EMAIL, VALID_PASSWORD), LOCAL_IP)
                .andExpect(status().isCreated());
        registerFrom(signUpBody("Jane", VALID_EMAIL, VALID_PASSWORD), LOCAL_IP)
                .andExpect(status().isConflict());
    }

    @Test
    void register_sixthAttemptFromOneAddress_afterFiveDuplicates_isTooManyRequests() throws Exception {
        registerFrom(signUpBody("Jane", VALID_EMAIL, VALID_PASSWORD), LOCAL_IP)
                .andExpect(status().isCreated());
        String duplicate = signUpBody("Jane", VALID_EMAIL, VALID_PASSWORD);
        for (int attempt = 0; attempt < 5; attempt++) {
            registerFrom(duplicate, REGISTRATION_IP).andExpect(status().isConflict());
        }
        registerFrom(duplicate, REGISTRATION_IP).andExpect(status().isTooManyRequests());
    }

    // ── list ─────────────────────────────────────────────────────────────

    @Test
    void list_noKey_isUnauthorized() throws Exception {
        mockMvc.perform(get(USERS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_listsUsers_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void moderator_listsUsers_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(moderator)))
                .andExpect(status().isOk());
    }

    @Test
    void list_negativePage_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_sortByUnknownField_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("sort", "bogus,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_unknownRole_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("roles", "FOO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_nameLongerThan255_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("name", "a".repeat(256)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_emailLongerThan255_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("email", "a".repeat(256)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_createdAtStartAfterEnd_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("createdAtStart", "2030-01-02T00:00:00Z")
                        .param("createdAtEnd", "2030-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_updatedAtStartAfterEnd_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("updatedAtStart", "2030-01-02T00:00:00Z")
                        .param("updatedAtEnd", "2030-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_negativeStartId_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("startId", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_startIdAfterEndId_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("startId", "5")
                        .param("endId", "3"))
                .andExpect(status().isBadRequest());
    }

    // ── get ──────────────────────────────────────────────────────────────

    @Test
    void anonymous_onGetUser_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(get(USERS + "/" + owner.id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_getsOwnUser_isOkWithETag() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(get(USERS + "/" + owner.id()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG));
    }

    @Test
    void plainUser_onGetUser_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser plain = registerUserAndIssueApiKey("Plain");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(get(USERS + "/" + owner.id()).header(HttpHeaders.AUTHORIZATION, bearer(plain)))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderator_getsUser_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(get(USERS + "/" + owner.id()).header(HttpHeaders.AUTHORIZATION, bearer(moderator)))
                .andExpect(status().isOk());
    }

    @Test
    void admin_getsUnknownUser_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        mockMvc.perform(get(UNKNOWN_USER).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    // ── history ──────────────────────────────────────────────────────────

    @Test
    void anonymous_onUserHistory_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        mockMvc.perform(get(USERS + "/" + owner.id() + "/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_seesUserHistory_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        assertThat(historyEntries(admin, userUrl + "/history")).isNotEmpty();
    }

    @Test
    void moderator_seesUserHistory_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        assertThat(historyEntries(moderator, userUrl + "/history")).isNotEmpty();
    }

    @Test
    void stranger_seesEmptyUserHistory_isOk() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        String userUrl = USERS + "/" + owner.id();

        mockMvc.perform(get(userUrl + "/history").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── historySnapshot ──────────────────────────────────────────────────

    @Test
    void anonymous_onUserHistorySnapshot_isUnauthorized() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();
        long oldest = oldestSnapshotId(admin, userUrl);

        mockMvc.perform(get(userUrl + "/history/" + oldest))
                .andExpect(status().isUnauthorized());
    }

    // ── update ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onUpdateUser_isUnauthorized() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_changesOwnName_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void admin_changesAnotherUsersName_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void moderator_editsAnotherUser_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void stranger_editsAnotherUser_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        String userUrl = USERS + "/" + owner.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_changesOwnRoleToModerator_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"New Name","role":"MODERATOR"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_changesOwnRole_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        String userUrl = USERS + "/" + admin.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_blankName_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"","role":"USER"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_nameLongerThan100_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"%s","role":"USER"}""".formatted("a".repeat(101))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_missingRole_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"New Name"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_unknownRole_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"New Name","role":"FOO"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_updatesUnknownUser_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(put(UNKNOWN_USER)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH)
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void owner_updatesUnknownUser_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(put(UNKNOWN_USER)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH)
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_staleIfMatchAfterSave_isPreconditionFailed() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();
        renameUser(admin, userUrl, "First Save");

        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH)
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void update_missingIfMatch_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();

        // missing If-Match must be a bad request
        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isBadRequest());
    }

    // ── restore ──────────────────────────────────────────────────────────

    @Test
    void anonymous_onRestoreUserSnapshot_isUnauthorized() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();
        renameUser(admin, userUrl, "Renamed");
        long oldest = oldestSnapshotId(admin, userUrl);

        mockMvc.perform(post(userUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stranger_restoresAnotherUsersSnapshot_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        String userUrl = USERS + "/" + owner.id();
        renameUser(admin, userUrl, "Renamed");
        long oldest = oldestSnapshotId(admin, userUrl);

        mockMvc.perform(post(userUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl)))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderator_restoresAnotherUsersSnapshot_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();
        renameUser(admin, userUrl, "Renamed");
        long oldest = oldestSnapshotId(admin, userUrl);

        mockMvc.perform(post(userUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl)))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_restoresOwnOldestSnapshot_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();
        renameUser(admin, userUrl, "Renamed");
        long oldest = oldestSnapshotId(admin, userUrl);

        mockMvc.perform(post(userUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, userUrl)))
                .andExpect(status().isOk());
    }

    @Test
    void owner_restoreWithStaleVersionAfterRename_isPreconditionFailed() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();
        renameUser(admin, userUrl, "Renamed");
        long oldest = oldestSnapshotId(admin, userUrl);

        mockMvc.perform(post(userUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void restore_missingIfMatch_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();
        renameUser(admin, userUrl, "Renamed");
        long oldest = oldestSnapshotId(admin, userUrl);

        // missing If-Match must be a bad request
        mockMvc.perform(post(userUrl + "/history/" + oldest + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_onUnknownUserSnapshotRestore_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String userUrl = USERS + "/" + owner.id();
        renameUser(admin, userUrl, "Renamed");

        mockMvc.perform(post(userUrl + "/history/" + UNKNOWN_ID + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl)))
                .andExpect(status().isNotFound());
    }

    // ── delete ───────────────────────────────────────────────────────────

    @Test
    void anonymous_onDeleteUser_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(delete(USERS + "/" + owner.id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stranger_deletesAnotherUser_isForbidden() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");

        mockMvc.perform(delete(USERS + "/" + owner.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderator_deletesAnotherUser_isForbidden() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(delete(USERS + "/" + owner.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator)))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_deletesSelf_isNoContent() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(delete(USERS + "/" + owner.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void admin_deletesAnotherUser_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(delete(USERS + "/" + owner.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
    }

    @Test
    void admin_deletesUnknownUser_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(delete(UNKNOWN_USER)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Registers a user and promotes it to MODERATOR as the given administrator. */
    private RegisteredUser moderatorOf(RegisteredUser admin) throws Exception {
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        String userUrl = USERS + "/" + moderator.id();
        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Moderator","role":"MODERATOR"}"""))
                .andExpect(status().isOk());
        return moderator;
    }

    /** Saves a new name and the USER role for the target as the administrator, so the target gains a history entry. */
    private void renameUser(RegisteredUser admin, String userUrl, String name) throws Exception {
        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"%s","role":"USER"}""".formatted(name)))
                .andExpect(status().isOk());
    }

    private ResultActions registerFrom(String body, String remoteAddress) throws Exception {
        return mockMvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON).content(body)
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                }));
    }

    private static String signUpBody(String name, String email, String password) {
        return """
                {"name":"%s","email":"%s","password":"%s"}""".formatted(name, email, password);
    }

    /** Returns the user's history list as the viewer sees it, newest entry first. */
    private List<Map<String, Object>> historyEntries(RegisteredUser viewer, String historyUrl) throws Exception {
        String json = mockMvc.perform(get(historyUrl).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> entries = JsonPath.read(json, "$");
        return entries;
    }

    /** Id of the oldest history snapshot, which is the last entry of the newest-first history. */
    private long oldestSnapshotId(RegisteredUser viewer, String userUrl) throws Exception {
        Map<String, Object> oldest = historyEntries(viewer, userUrl + "/history").getLast();
        return ((Number) oldest.get("snapshotId")).longValue();
    }

    /** Reads the target's current ETag as the viewer, so a write can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
