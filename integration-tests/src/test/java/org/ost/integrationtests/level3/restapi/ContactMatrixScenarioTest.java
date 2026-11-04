package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 matrix: every contact REST operation (channel lists, reveals for provider profiles and advertisements, view counts) across the anonymous, owner, moderator and administrator roles, with its validation and rate-limit edges. */
@Level3ScenarioTest
class ContactMatrixScenarioTest extends AbstractRestApiScenarioTest {

    private static final String PROFILES = "/api/provider-profiles";
    private static final String ADS = "/api/advertisements";
    private static final String UNKNOWN_ID = "999999";
    private static final String PHONE = "+380501234567";
    private static final String TELEGRAM = "ivan_master";
    private static final String PROFILE_BODY = """
            {"kind":"MASTER","about":"Experienced plumber","phone":"+380501234567","telegram":"ivan_master"}""";
    private static final String VIBER_PROFILE_BODY = """
            {"kind":"MASTER","about":"Experienced plumber","viber":"+380501234568"}""";
    private static final String BARE_PROFILE_BODY = """
            {"kind":"MASTER","about":"Experienced plumber"}""";

    // ── providerProfileContact ───────────────────────────────────────────

    @Test
    void unknownProfile_contact_hasNoChannels() throws Exception {
        mockMvc.perform(get(PROFILES + "/" + UNKNOWN_ID + "/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels").isEmpty());
    }

    @Test
    void profileWithoutContact_hasNoChannels() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProfile(owner, BARE_PROFILE_BODY);

        mockMvc.perform(get(PROFILES + "/" + profileId + "/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels").isEmpty());
    }

    // ── revealProviderProfile ────────────────────────────────────────────

    @Test
    void profileReveal_emptyBody_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProfile(owner, PROFILE_BODY);

        mockMvc.perform(rawReveal(PROFILES + "/" + profileId + "/contact/reveals", "{}", "10.2.0.7"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profileReveal_unknownChannel_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProfile(owner, PROFILE_BODY);

        mockMvc.perform(anonymousReveal(PROFILES + "/" + profileId + "/contact/reveals", "FOO", "10.2.0.8"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profileReveal_noBody_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProfile(owner, PROFILE_BODY);

        mockMvc.perform(post(PROFILES + "/" + profileId + "/contact/reveals").with(remote("10.2.0.9")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProfile_reveal_isNotFound() throws Exception {
        mockMvc.perform(anonymousReveal(PROFILES + "/" + UNKNOWN_ID + "/contact/reveals", "PHONE", "10.2.0.10"))
                .andExpect(status().isNotFound());
    }

    @Test
    void owner_revealsPhone_isOk() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProfile(owner, PROFILE_BODY);

        mockMvc.perform(revealAs(owner, PROFILES + "/" + profileId + "/contact/reveals", "PHONE", "10.2.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(PHONE));
    }

    @Test
    void admin_revealsTelegram_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProfile(owner, PROFILE_BODY);

        mockMvc.perform(revealAs(admin, PROFILES + "/" + profileId + "/contact/reveals", "TELEGRAM", "10.2.0.2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(TELEGRAM));
    }

    @Test
    void viberReveal_returnsDeepLink() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProfile(owner, VIBER_PROFILE_BODY);

        mockMvc.perform(anonymousReveal(PROFILES + "/" + profileId + "/contact/reveals", "VIBER", "10.2.0.3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deepLink").value(startsWith("viber://chat?number=")));
    }

    // ── revealAdvertisement ──────────────────────────────────────────────

    @Test
    void adReveal_emptyBody_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long adId = createAdvertisement(owner);

        mockMvc.perform(rawReveal(ADS + "/" + adId + "/contact/reveals", "{}", "10.2.0.11"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adReveal_unknownChannel_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long adId = createAdvertisement(owner);

        mockMvc.perform(anonymousReveal(ADS + "/" + adId + "/contact/reveals", "FOO", "10.2.0.12"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAd_reveal_isNotFound() throws Exception {
        mockMvc.perform(anonymousReveal(ADS + "/" + UNKNOWN_ID + "/contact/reveals", "PHONE", "10.2.0.13"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adWithoutOwnersProfile_reveal_isNotFound() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long adId = createAdvertisement(owner);

        mockMvc.perform(anonymousReveal(ADS + "/" + adId + "/contact/reveals", "PHONE", "10.2.0.5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adWithOwnersViberProfile_revealsDeepLink() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        createProfile(owner, VIBER_PROFILE_BODY);
        long adId = createAdvertisement(owner);

        mockMvc.perform(anonymousReveal(ADS + "/" + adId + "/contact/reveals", "VIBER", "10.2.0.4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deepLink").value(startsWith("viber://chat?number=")));
    }

    @Test
    void adReveal_isLimited_after30() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        createProfile(owner, PROFILE_BODY);
        long adId = createAdvertisement(owner);
        String path = ADS + "/" + adId + "/contact/reveals";

        for (int i = 0; i < 30; i++) {
            mockMvc.perform(anonymousReveal(path, "PHONE", "10.2.0.6")).andExpect(status().isOk());
        }

        mockMvc.perform(anonymousReveal(path, "PHONE", "10.2.0.6"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"));
    }

    // ── views ────────────────────────────────────────────────────────────

    @Test
    void admin_seesProfileViews_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProfile(owner, PROFILE_BODY);

        mockMvc.perform(get(PROFILES + "/" + profileId + "/contact/views")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void moderator_seesProfileViews_isOk() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProfile(owner, PROFILE_BODY);

        mockMvc.perform(get(PROFILES + "/" + profileId + "/contact/views")
                        .header(HttpHeaders.AUTHORIZATION, bearer(moderator)))
                .andExpect(status().isOk());
    }

    @Test
    void admin_onUnknownProfileViews_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get(PROFILES + "/" + UNKNOWN_ID + "/contact/views")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void owner_onNeverRevealedProfileViews_isOkWithEmptyList() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProfile(owner, PROFILE_BODY);

        mockMvc.perform(get(PROFILES + "/" + profileId + "/contact/views")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Registers a user and promotes it to MODERATOR as the given administrator. */
    private RegisteredUser moderatorOf(RegisteredUser admin) throws Exception {
        RegisteredUser moderator = registerUserAndIssueApiKey("Moderator");
        String userUrl = "/api/users/" + moderator.id();
        mockMvc.perform(put(userUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(HttpHeaders.IF_MATCH, etagOf(admin, userUrl))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Moderator","role":"MODERATOR"}"""))
                .andExpect(status().isOk());
        return moderator;
    }

    /** Creates one provider profile from the given body as the owner and returns its id. */
    private long createProfile(RegisteredUser owner, String body) throws Exception {
        String profileResponse = mockMvc.perform(post(PROFILES)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(profileResponse);
    }

    /** Creates one advertisement titled "Original" as the given owner and returns its id. */
    private long createAdvertisement(RegisteredUser owner) throws Exception {
        String createResponse = mockMvc.perform(post(ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"Original","description":"Desc","adKind":"OFFER"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(createResponse);
    }

    /** Anonymous reveal of one channel sent from the given remote address, so each scenario has its own rate-limit bucket. */
    private MockHttpServletRequestBuilder anonymousReveal(String path, String channel, String remoteAddress) {
        return rawReveal(path, """
                {"channel":"%s"}""".formatted(channel), remoteAddress);
    }

    /** Reveal of one channel as the given viewer, identified by a bearer key, sent from the given remote address. */
    private MockHttpServletRequestBuilder revealAs(RegisteredUser viewer, String path, String channel, String remoteAddress) {
        return rawReveal(path, """
                {"channel":"%s"}""".formatted(channel), remoteAddress)
                .header(HttpHeaders.AUTHORIZATION, bearer(viewer));
    }

    /** Reveal request with an exact JSON body sent from the given remote address, for body shapes the channel helpers cannot produce. */
    private MockHttpServletRequestBuilder rawReveal(String path, String body, String remoteAddress) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON).content(body)
                .with(remote(remoteAddress));
    }

    private static RequestPostProcessor remote(String remoteAddress) {
        return request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        };
    }

    /** Reads the resource's current ETag as the viewer, so a write can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
