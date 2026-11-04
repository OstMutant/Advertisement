package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: public contact channels and rate-limited reveals, owner-only view counts, and an advertisement showing its owner's profile contact. */
@Level3ScenarioTest
class ContactScenarioTest extends AbstractRestApiScenarioTest {

    private static final String PHONE = "+380501234567";
    private static final String TELEGRAM = "ivan_master";

    @Test
    void visitor_seesChannels_andRevealsPhone_thenOwnerSeesOneView() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(get("/api/provider-profiles/" + profileId + "/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels[0]").value("PHONE"))
                .andExpect(jsonPath("$.channels[1]").value("TELEGRAM"));

        mockMvc.perform(anonymousReveal("/api/provider-profiles/" + profileId + "/contact/reveals", "PHONE", "10.1.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(PHONE))
                .andExpect(jsonPath("$.deepLink").doesNotExist());

        mockMvc.perform(get("/api/provider-profiles/" + profileId + "/contact/views")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channel").value("PHONE"))
                .andExpect(jsonPath("$[0].count").value(1));
    }

    @Test
    void telegramReveal_returnsDeepLink() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(anonymousReveal("/api/provider-profiles/" + profileId + "/contact/reveals", "TELEGRAM", "10.1.0.4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(TELEGRAM))
                .andExpect(jsonPath("$.deepLink").value("https://t.me/" + TELEGRAM));
    }

    @Test
    void channelNotSet_isNotFound() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(anonymousReveal("/api/provider-profiles/" + profileId + "/contact/reveals", "VIBER", "10.1.0.5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reveal_isLimited_after30() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProviderProfile(owner);
        String path = "/api/provider-profiles/" + profileId + "/contact/reveals";

        for (int i = 0; i < 30; i++) {
            mockMvc.perform(anonymousReveal(path, "PHONE", "10.1.0.2")).andExpect(status().isOk());
        }

        mockMvc.perform(anonymousReveal(path, "PHONE", "10.1.0.2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"));
    }

    @Test
    void views_otherUserForbidden_visitorUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        long profileId = createProviderProfile(owner);

        mockMvc.perform(get("/api/provider-profiles/" + profileId + "/contact/views")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.rawApiKey()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/provider-profiles/" + profileId + "/contact/views"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    @Test
    void advertisement_withoutOwnContact_fallsBackToOwnersProfile() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        createProviderProfile(owner);
        long adId = createAdvertisement(owner);

        mockMvc.perform(anonymousReveal("/api/advertisements/" + adId + "/contact/reveals", "PHONE", "10.1.0.3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(PHONE));
    }

    @Test
    void advertisement_contactListsOwnersProfileChannels() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        createProviderProfile(owner);
        long adId = createAdvertisement(owner);

        mockMvc.perform(get("/api/advertisements/" + adId + "/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels[0]").value("PHONE"))
                .andExpect(jsonPath("$.channels[1]").value("TELEGRAM"));
    }

    @Test
    void advertisement_contactWithValidKey_listsSameChannels() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        createProviderProfile(owner);
        long adId = createAdvertisement(owner);

        mockMvc.perform(get("/api/advertisements/" + adId + "/contact")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels[0]").value("PHONE"))
                .andExpect(jsonPath("$.channels[1]").value("TELEGRAM"));
    }

    @Test
    void advertisement_contactWithoutOwnersProfile_hasNoChannels() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long adId = createAdvertisement(owner);

        mockMvc.perform(get("/api/advertisements/" + adId + "/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels").isEmpty());
    }

    @Test
    void advertisement_contactForUnknownId_hasNoChannels() throws Exception {
        mockMvc.perform(get("/api/advertisements/999999/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels").isEmpty());
    }

    /** Creates a MASTER provider profile with a phone and a Telegram handle as the given owner and returns its id. */
    private long createProviderProfile(RegisteredUser owner) throws Exception {
        String profileBody = """
                {"kind":"MASTER","about":"Experienced plumber","phone":"%s","telegram":"%s"}""".formatted(PHONE, TELEGRAM);
        String profileResponse = mockMvc.perform(post("/api/provider-profiles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(profileBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(profileResponse);
    }

    /** Creates one advertisement titled "Original" as the given owner and returns its id. */
    private long createAdvertisement(RegisteredUser owner) throws Exception {
        String createBody = """
                {"title":"Original","description":"Desc","adKind":"OFFER"}""";
        String createResponse = mockMvc.perform(post("/api/advertisements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(createResponse);
    }

    /** Anonymous reveal of one channel sent from the given remote address, so each scenario has its own rate-limit bucket. */
    private MockHttpServletRequestBuilder anonymousReveal(String path, String channel, String remoteAddress) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"channel":"%s"}""".formatted(channel))
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                });
    }
}
