package org.ost.integrationtests.level3.restapi;

import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: unauthenticated infrastructure endpoints -- the health probe and the sitemap that lists public advertisements and provider profiles. */
@Level3ScenarioTest
class PublicEndpointsScenarioTest extends AbstractRestApiScenarioTest {

    private static final String PHONE = "+380501234567";
    private static final String TELEGRAM = "ivan_master";

    @Test
    void health_isOkWithoutKey() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void health_isOkWithValidKey() throws Exception {
        RegisteredUser user = registerUserAndIssueApiKey("Admin");

        mockMvc.perform(get("/health").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void sitemap_isXmlUrlset() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("urlset")));
    }

    @Test
    void sitemap_listsAdsAndProfiles_andDropsDeletedAd() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        long profileId = createProviderProfile(owner);
        long adId = createAdvertisement(owner);

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/ads/" + adId)))
                .andExpect(content().string(containsString("/providers/" + profileId)));

        String adUrl = "/api/advertisements/" + adId;
        mockMvc.perform(delete(adUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/ads/" + adId))));
    }

    /** Creates a MASTER provider profile with a phone and a Telegram handle as the given owner and returns its id. */
    private long createProviderProfile(RegisteredUser owner) throws Exception {
        String profileBody = """
                {"kind":"MASTER","about":"Experienced plumber","phone":"%s","telegram":"%s"}""".formatted(PHONE, TELEGRAM);
        String profileResponse = mockMvc.perform(post("/api/provider-profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonScenarioUtils.extractId(createResponse);
    }

    /** Reads the advertisement's current ETag as the owner, so a delete can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    private static String bearer(RegisteredUser user) {
        return "Bearer " + user.rawApiKey();
    }
}
