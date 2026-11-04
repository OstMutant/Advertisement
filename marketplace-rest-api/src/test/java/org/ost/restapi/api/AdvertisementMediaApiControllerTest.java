package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.AdvertisementMediaService;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.model.AttachmentLimits;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.authenticateAs;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.clearAuthentication;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.mockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fast MockMvc contract tests for {@link AdvertisementMediaApiController} -- mocked orchestrator service, no DB. */
@ExtendWith(MockitoExtension.class)
class AdvertisementMediaApiControllerTest {

    private static final Long ACTOR_ID = 10L;
    private static final Long AD_ID = 7L;
    private static final String MEDIA_PATH = "/api/advertisements/7/media";
    private static final String VIDEO_PATH = "/api/advertisements/7/media/videos";
    private static final String VIDEO_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G'};

    @Mock private AdvertisementMediaService mediaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new AdvertisementMediaApiController(mediaService));
        authenticateAs(ACTOR_ID);
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    @Test
    void list_returnsTheGalleryItems() throws Exception {
        when(mediaService.list(AD_ID)).thenReturn(List.of(new AttachmentItemDto(1L, "http://storage/a.png", "a.png", "image/png")));

        mockMvc.perform(get(MEDIA_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].url").value("http://storage/a.png"))
                .andExpect(jsonPath("$[0].contentType").value("image/png"));
    }

    @Test
    void list_unknownAdvertisement_returnsNotFound() throws Exception {
        when(mediaService.list(AD_ID)).thenThrow(new NoSuchElementException("Advertisement 7 not found"));

        mockMvc.perform(get(MEDIA_PATH))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_returnsCreatedItems() throws Exception {
        when(mediaService.uploadAll(eq(ACTOR_ID), eq(AD_ID), anyList()))
                .thenReturn(List.of(new AttachmentItemDto(1L, "http://storage/a.png", "a.png", "image/png")));

        mockMvc.perform(multipart(MEDIA_PATH).file(new MockMultipartFile("files", "a.png", "image/png", PNG_BYTES)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].url").value("http://storage/a.png"))
                .andExpect(jsonPath("$[0].filename").value("a.png"));
    }

    @Test
    void upload_withoutFiles_returnsBadRequest() throws Exception {
        mockMvc.perform(multipart(MEDIA_PATH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_moreThanTenFiles_isBadRequestWithoutCallingTheService() throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(MEDIA_PATH);
        for (int i = 0; i <= AttachmentLimits.MAX_FILES; i++) {
            request.file(new MockMultipartFile("files", "f" + i + ".png", "image/png", PNG_BYTES));
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mediaService);
    }

    @Test
    void upload_serviceRejectsTheBatch_returnsBadRequest() throws Exception {
        when(mediaService.uploadAll(eq(ACTOR_ID), eq(AD_ID), anyList()))
                .thenThrow(new IllegalArgumentException("A gallery holds at most 10 items"));

        mockMvc.perform(multipart(MEDIA_PATH).file(new MockMultipartFile("files", "a.png", "image/png", PNG_BYTES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A gallery holds at most 10 items"));
    }

    @Test
    void upload_notAnEditor_returnsForbidden() throws Exception {
        when(mediaService.uploadAll(eq(ACTOR_ID), eq(AD_ID), anyList()))
                .thenThrow(new AccessDeniedException("not the owner"));

        mockMvc.perform(multipart(MEDIA_PATH).file(new MockMultipartFile("files", "a.png", "image/png", PNG_BYTES)))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_unknownAdvertisement_returnsNotFound() throws Exception {
        when(mediaService.uploadAll(eq(ACTOR_ID), eq(AD_ID), anyList()))
                .thenThrow(new NoSuchElementException("Advertisement 7 not found"));

        mockMvc.perform(multipart(MEDIA_PATH).file(new MockMultipartFile("files", "a.png", "image/png", PNG_BYTES)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addVideo_returnsCreatedItem() throws Exception {
        when(mediaService.addVideo(ACTOR_ID, AD_ID, VIDEO_URL))
                .thenReturn(new AttachmentItemDto(2L, VIDEO_URL, "youtube", "youtube"));

        mockMvc.perform(post(VIDEO_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + VIDEO_URL + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void addVideo_blankUrl_returnsBadRequest() throws Exception {
        mockMvc.perform(post(VIDEO_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_ownItem_returnsNoContent() throws Exception {
        mockMvc.perform(delete(MEDIA_PATH + "/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_itemOfAnotherAdvertisement_returnsNotFound() throws Exception {
        doThrow(new NoSuchElementException("not on this advertisement"))
                .when(mediaService).delete(ACTOR_ID, AD_ID, 99L);

        mockMvc.perform(delete(MEDIA_PATH + "/99"))
                .andExpect(status().isNotFound());
    }
}
