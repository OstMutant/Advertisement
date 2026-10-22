package org.ost.integrationtests.level3.restapi;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.integrationtests.support.AbstractRestApiScenarioTest;
import org.ost.integrationtests.support.JsonScenarioUtils;
import org.ost.integrationtests.support.Level3ScenarioTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 matrix: every advertisement media REST operation (gallery list, upload, video link, item delete) across the anonymous, owner, moderator and administrator roles, with its validation edges. */
@Level3ScenarioTest
class AdvertisementMediaMatrixScenarioTest extends AbstractRestApiScenarioTest {

    private static final String ADS = "/api/advertisements";
    private static final String UNKNOWN_MEDIA = ADS + "/999999/media";
    private static final String VIDEO_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, (byte) 0x1F, (byte) 0x15, (byte) 0xC4, (byte) 0x89,
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
            0x0D, 0x0A, 0x2D, (byte) 0xB4,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};

    @BeforeEach
    void stubStorage() throws Exception {
        when(storageService.upload(any(), any(), any(), anyLong(), any()))
                .thenAnswer(invocation -> "http://storage.test/" + UUID.randomUUID());
    }

    // ── list media ───────────────────────────────────────────────────────

    @Test
    void unknownAd_listMedia_isNotFound() throws Exception {
        mockMvc.perform(get(UNKNOWN_MEDIA))
                .andExpect(status().isNotFound());
    }

    // ── upload ───────────────────────────────────────────────────────────

    @Test
    void unknownAd_upload_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        uploadPng(admin, UNKNOWN_MEDIA).andExpect(status().isNotFound());
    }

    @Test
    void admin_uploadsMedia_isCreated() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String mediaUrl = adUrl(owner) + "/media";

        uploadPng(admin, mediaUrl).andExpect(status().isCreated());
    }

    @Test
    void moderator_uploadsMedia_isCreated() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String mediaUrl = adUrl(owner) + "/media";

        uploadPng(moderator, mediaUrl).andExpect(status().isCreated());
    }

    @Test
    void upload_missingFilesPart_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String mediaUrl = adUrl(owner) + "/media";

        mockMvc.perform(multipart(mediaUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    // ── addVideo ─────────────────────────────────────────────────────────

    @Test
    void anonymous_onAddVideo_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        mockMvc.perform(post(adUrl(owner) + "/media/videos")
                        .contentType(MediaType.APPLICATION_JSON).content(videoBody(VIDEO_URL)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownAd_addVideo_isNotFound() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");

        postVideo(admin, ADS + "/999999", VIDEO_URL).andExpect(status().isNotFound());
    }

    @Test
    void blankUrl_addVideo_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        postVideo(owner, adUrl(owner), "").andExpect(status().isBadRequest());
    }

    @Test
    void urlLongerThan2048_addVideo_isBadRequest() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String longUrl = "https://www.youtube.com/watch?v=" + "a".repeat(2100);

        postVideo(owner, adUrl(owner), longUrl).andExpect(status().isBadRequest());
    }

    @Test
    void admin_addsVideo_isCreated() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        postVideo(admin, adUrl(owner), VIDEO_URL).andExpect(status().isCreated());
    }

    @Test
    void moderator_addsVideo_isCreated() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");

        postVideo(moderator, adUrl(owner), VIDEO_URL).andExpect(status().isCreated());
    }

    // ── deleteMedia ──────────────────────────────────────────────────────

    @Test
    void anonymous_onDeleteMedia_isUnauthorized() throws Exception {
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String mediaUrl = adUrl(owner) + "/media";
        long mediaId = firstMediaId(uploadPng(owner, mediaUrl).andExpect(status().isCreated()));

        mockMvc.perform(delete(mediaUrl + "/" + mediaId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_deletesMedia_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String mediaUrl = adUrl(owner) + "/media";
        long mediaId = firstMediaId(uploadPng(owner, mediaUrl).andExpect(status().isCreated()));

        mockMvc.perform(delete(mediaUrl + "/" + mediaId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
    }

    @Test
    void moderator_deletesMedia_isNoContent() throws Exception {
        RegisteredUser admin = registerUserAndIssueApiKey("Admin");
        RegisteredUser moderator = moderatorOf(admin);
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String mediaUrl = adUrl(owner) + "/media";
        long mediaId = firstMediaId(uploadPng(owner, mediaUrl).andExpect(status().isCreated()));

        mockMvc.perform(delete(mediaUrl + "/" + mediaId).header(HttpHeaders.AUTHORIZATION, bearer(moderator)))
                .andExpect(status().isNoContent());
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

    /** Creates one advertisement as the owner and returns its URL. */
    private String adUrl(RegisteredUser owner) throws Exception {
        String createResponse = mockMvc.perform(post(ADS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"Gallery","description":"Desc","adKind":"OFFER"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ADS + "/" + JsonScenarioUtils.extractId(createResponse);
    }

    private ResultActions uploadPng(RegisteredUser user, String mediaUrl) throws Exception {
        return mockMvc.perform(multipart(mediaUrl)
                .file(new MockMultipartFile("files", "pic.png", "image/png", PNG_BYTES))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions postVideo(RegisteredUser user, String adUrl, String url) throws Exception {
        return mockMvc.perform(post(adUrl + "/media/videos")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(videoBody(url)));
    }

    private static String videoBody(String url) {
        return "{\"url\":\"" + url + "\"}";
    }

    private static long firstMediaId(ResultActions upload) throws Exception {
        String json = upload.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].id")).longValue();
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
