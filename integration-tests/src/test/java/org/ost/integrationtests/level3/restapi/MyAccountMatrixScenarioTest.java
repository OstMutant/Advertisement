package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 matrix: the caller's own account REST operations -- language switch and page-size settings -- with their validation, precondition and authentication edges. */
@Level3ScenarioTest
class MyAccountMatrixScenarioTest extends AbstractRestApiScenarioTest {

    private static final String LOCALE_URL = "/api/users/me/locale";
    private static final String SETTINGS_URL = "/api/users/me/settings";
    private static final String STALE_IF_MATCH = "\"0\"";
    private static final String VALID_SETTINGS = settingsBody("20", "20", "20", "20");

    // ── changeMyLocale ───────────────────────────────────────────────────

    @Test
    void anonymous_onChangeLocale_isUnauthorized() throws Exception {
        mockMvc.perform(put(LOCALE_URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"locale":"uk"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void user_changesLocaleToUkrainian_isNoContent() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        mockMvc.perform(put(LOCALE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"locale":"uk"}"""))
                .andExpect(status().isNoContent());
    }

    @Test
    void user_changesLocaleWithoutLocaleField_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        mockMvc.perform(put(LOCALE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void user_changesLocaleToUnsupported_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        mockMvc.perform(put(LOCALE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"locale":"fr"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void user_changesLocaleWithoutBody_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        mockMvc.perform(put(LOCALE_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isBadRequest());
    }

    // ── updateMySettings ─────────────────────────────────────────────────

    @Test
    void anonymous_onUpdateSettings_isUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        mockMvc.perform(patch(SETTINGS_URL)
                        .header(HttpHeaders.IF_MATCH, etagOf(user))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_SETTINGS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void settings_adsPageSizeBelowMinimum_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        patchSettings(user, settingsBody("4", "20", "20", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settings_adsPageSizeAboveMaximum_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        patchSettings(user, settingsBody("101", "20", "20", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settings_usersPageSizeBelowMinimum_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        patchSettings(user, settingsBody("20", "4", "20", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settings_usersPageSizeAboveMaximum_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        patchSettings(user, settingsBody("20", "101", "20", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settings_timelinePageSizeBelowMinimum_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        patchSettings(user, settingsBody("20", "20", "4", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settings_timelinePageSizeAboveMaximum_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        patchSettings(user, settingsBody("20", "20", "101", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settings_providerProfilesPageSizeBelowMinimum_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        patchSettings(user, settingsBody("20", "20", "20", "4"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settings_providerProfilesPageSizeAboveMaximum_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        patchSettings(user, settingsBody("20", "20", "20", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settings_missingField_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        patchSettings(user, """
                {"usersPageSize":20,"timelinePageSize":20,"providerProfilesPageSize":20}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void settings_nonNumericField_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        patchSettings(user, settingsBody("\"abc\"", "20", "20", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settings_staleIfMatchAfterSave_isPreconditionFailed() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");
        patchSettings(user, VALID_SETTINGS).andExpect(status().isOk());

        mockMvc.perform(patch(SETTINGS_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(HttpHeaders.IF_MATCH, STALE_IF_MATCH)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_SETTINGS))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void settings_missingIfMatch_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser user = registerUserAndIssueApiKey("User");

        // missing If-Match must be a bad request
        mockMvc.perform(patch(SETTINGS_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_SETTINGS))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Builds the settings body from four raw JSON values, so a case can pass a bad literal such as a string. */
    private static String settingsBody(String adsPageSize, String usersPageSize, String timelinePageSize, String providerProfilesPageSize) {
        return """
                {"adsPageSize":%s,"usersPageSize":%s,"timelinePageSize":%s,"providerProfilesPageSize":%s}""".formatted(
                adsPageSize, usersPageSize, timelinePageSize, providerProfilesPageSize);
    }

    /** Sends a settings PATCH as the user, with the current ETag fetched first so only the body under test decides the outcome. */
    private ResultActions patchSettings(RegisteredUser user, String body) throws Exception {
        return mockMvc.perform(patch(SETTINGS_URL)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header(HttpHeaders.IF_MATCH, etagOf(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Reads the user's own settings ETag, so a PATCH can send it back in If-Match. */
    private String etagOf(RegisteredUser user) throws Exception {
        return mockMvc.perform(get(SETTINGS_URL).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
