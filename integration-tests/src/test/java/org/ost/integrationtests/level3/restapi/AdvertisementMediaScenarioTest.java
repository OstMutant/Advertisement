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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Level 3 scenario: an advertisement's photo and video gallery through its REST media endpoints, with the history and restore of the gallery. */
@Level3ScenarioTest
class AdvertisementMediaScenarioTest extends AbstractRestApiScenarioTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, (byte) 0x1F, (byte) 0x15, (byte) 0xC4, (byte) 0x89,
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
            0x0D, 0x0A, 0x2D, (byte) 0xB4,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
    private static final String YOUTUBE_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @BeforeEach
    void stubStorage() throws Exception {
        when(storageService.upload(any(), any(), any(), anyLong(), any()))
                .thenAnswer(invocation -> "http://storage.test/" + UUID.randomUUID());
    }

    @Test
    void owner_uploadsListsAndDeletesMedia() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String mediaUrl = "/api/advertisements/" + createAdvertisement(owner, "Gallery") + "/media";

        long mediaId = firstMediaId(uploadPng(owner, mediaUrl).andExpect(status().isCreated()));

        mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].url").isNotEmpty())
                .andExpect(jsonPath("$[0].contentType").value("image/png"));

        mockMvc.perform(delete(mediaUrl + "/" + mediaId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(mediaUrl))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void video_linkIsAccepted_otherHostIsBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = "/api/advertisements/" + createAdvertisement(owner, "Videos");

        postVideo(owner, adUrl, YOUTUBE_URL).andExpect(status().isCreated());
        postVideo(owner, adUrl, "https://example.com/x").andExpect(status().isBadRequest());
    }

    @Test
    void stranger_cannotAddOrDelete_anonymousIsUnauthorized() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        RegisteredUser stranger = registerUserAndIssueApiKey("Stranger");
        String adUrl = "/api/advertisements/" + createAdvertisement(owner, "Guarded");
        String mediaUrl = adUrl + "/media";
        long mediaId = firstMediaId(uploadPng(owner, mediaUrl).andExpect(status().isCreated()));

        uploadPng(stranger, mediaUrl).andExpect(status().isForbidden());
        postVideo(stranger, adUrl, YOUTUBE_URL).andExpect(status().isForbidden());
        mockMvc.perform(delete(mediaUrl + "/" + mediaId).header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.rawApiKey()))
                .andExpect(status().isForbidden());

        MvcResult anonymous = mockMvc.perform(multipart(mediaUrl)
                        .file(new MockMultipartFile("files", "pic.png", "image/png", PNG_BYTES)))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(anonymous.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @Test
    void foreignAttachmentId_isNotFound() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser ownerA = registerUserAndIssueApiKey("OwnerA");
        RegisteredUser ownerB = registerUserAndIssueApiKey("OwnerB");
        String mediaUrlA = "/api/advertisements/" + createAdvertisement(ownerA, "A") + "/media";
        String adUrlB = "/api/advertisements/" + createAdvertisement(ownerB, "B");
        long attachmentOfA = firstMediaId(uploadPng(ownerA, mediaUrlA).andExpect(status().isCreated()));

        mockMvc.perform(delete(adUrlB + "/media/" + attachmentOfA).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerB.rawApiKey()))
                .andExpect(status().isNotFound());
    }

    @Test
    void eleventhItem_isBadRequest() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String mediaUrl = "/api/advertisements/" + createAdvertisement(owner, "Full") + "/media";

        for (int i = 0; i < 10; i++) {
            uploadPng(owner, mediaUrl).andExpect(status().isCreated());
        }
        uploadPng(owner, mediaUrl).andExpect(status().isBadRequest());

        mockMvc.perform(get(mediaUrl)).andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    void batchOfThreeFiles_recordsOneHistoryEntry() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = "/api/advertisements/" + createAdvertisement(owner, "Batch");
        String mediaUrl = adUrl + "/media";
        int entriesBefore = historyEntries(owner, adUrl + "/history").size();

        MockMultipartHttpServletRequestBuilder request = multipart(mediaUrl);
        for (int i = 1; i <= 3; i++) {
            request.file(new MockMultipartFile("files", "pic" + i + ".png", "image/png", PNG_BYTES));
        }
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey());

        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get(mediaUrl))
                .andExpect(jsonPath("$.length()").value(3));
        assertThat(historyEntries(owner, adUrl + "/history")).hasSize(entriesBefore + 1);
    }

    @Test
    void mediaChange_appearsInTheHistory() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = "/api/advertisements/" + createAdvertisement(owner, "Logged");
        int entriesBefore = historyEntries(owner, adUrl + "/history").size();

        uploadPng(owner, adUrl + "/media").andExpect(status().isCreated());

        assertThat(historyEntries(owner, adUrl + "/history")).hasSizeGreaterThan(entriesBefore);
    }

    @Test
    void restoreBringsTheGalleryBack() throws Exception {
        registerUserAndIssueApiKey("Admin");
        RegisteredUser owner = registerUserAndIssueApiKey("Owner");
        String adUrl = "/api/advertisements/" + createAdvertisement(owner, "Restorable");
        String mediaUrl = adUrl + "/media";
        String urlA = JsonPath.read(uploadPng(owner, mediaUrl).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$[0].url");
        long galleryWithA = snapshotIdOf(historyEntries(owner, adUrl + "/history").getFirst());

        uploadPng(owner, mediaUrl).andExpect(status().isCreated());

        mockMvc.perform(post(adUrl + "/history/" + galleryWithA + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey())
                        .header(HttpHeaders.IF_MATCH, etagOf(owner, adUrl)))
                .andExpect(status().isOk());

        mockMvc.perform(get(mediaUrl))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].url").value(urlA));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private ResultActions uploadPng(RegisteredUser user, String mediaUrl) throws Exception {
        return mockMvc.perform(multipart(mediaUrl)
                .file(new MockMultipartFile("files", "pic.png", "image/png", PNG_BYTES))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey()));
    }

    private ResultActions postVideo(RegisteredUser user, String adUrl, String url) throws Exception {
        return mockMvc.perform(post(adUrl + "/media/videos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.rawApiKey())
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"" + url + "\"}"));
    }

    private static long firstMediaId(ResultActions upload) throws Exception {
        String json = upload.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].id")).longValue();
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

    /** Reads the resource's current ETag as the viewer, so a restore can send it back in If-Match. */
    private String etagOf(RegisteredUser viewer, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer.rawApiKey()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }
}
