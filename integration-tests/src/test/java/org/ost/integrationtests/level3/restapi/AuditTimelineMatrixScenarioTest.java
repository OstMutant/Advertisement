package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 matrix: the global audit timeline across the anonymous, moderator and administrator roles, with its sort, filter and paging validation edges. */
@Level3ScenarioTest
class AuditTimelineMatrixScenarioTest extends AbstractRestApiScenarioTest {

    private static final String TIMELINE = "/api/audit/timeline";

    // ── timeline ─────────────────────────────────────────────────────────

    @Test
    void anonymous_onTimeline_isUnauthorized() throws Exception {
        mockMvc.perform(get(TIMELINE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_readsTimeline_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(TIMELINE).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void moderator_readsTimeline_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);

        mockMvc.perform(get(TIMELINE).header(HttpHeaders.AUTHORIZATION, bearer(moderator)))
                .andExpect(status().isOk());
    }

    @Test
    void timeline_sortByUnknownField_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(TIMELINE).param("sort", "bogus,asc").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timeline_unknownEntityType_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(TIMELINE).param("entityTypes", "FOO").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timeline_unknownActionType_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(TIMELINE).param("actionTypes", "FOO").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timeline_nonNumericActorId_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(TIMELINE).param("actorIds", "abc").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timeline_fromDateAfterToDate_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(TIMELINE)
                        .param("fromDate", "2030-01-02T00:00:00Z")
                        .param("toDate", "2030-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timeline_negativePage_isBadRequest() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(TIMELINE).param("page", "-1").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Registers a user and promotes it to MODERATOR as the given administrator. */
    private RegisteredUser moderatorOf(RegisteredUser admin) throws Exception {
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        String userUrl = "/api/users/" + moderator.id();
        String etag = mockMvc.perform(get(userUrl).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Moderator","role":"MODERATOR"}"""))
                .andExpect(status().isOk());
        return moderator;
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
