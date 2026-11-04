package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.ContactAccessService;
import org.ost.orchestrator.services.ContactRevealService;
import org.ost.platform.contact.dto.ContactViewCountDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.TooManyAttemptsException;
import org.ost.platform.core.model.EntityType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.authenticateAs;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.clearAuthentication;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.mockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fast MockMvc contract tests for {@link ContactApiController} -- mocked orchestrator services, no DB. */
@ExtendWith(MockitoExtension.class)
class ContactApiControllerTest {

    private static final Long ACTOR_ID = 10L;
    private static final Long PROFILE_ID = 7L;

    @Mock private ContactRevealService contactRevealService;
    @Mock private ContactAccessService contactAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new ContactApiController(contactRevealService, contactAccessService));
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    // ── channels (public) ────────────────────────────────────────────────────

    @Test
    void providerProfileChannels_listsSetChannelsWithoutValues() throws Exception {
        when(contactRevealService.availableChannels(EntityType.PROVIDER_PROFILE, PROFILE_ID)).thenReturn(List.of(ContactChannel.PHONE));

        mockMvc.perform(get("/api/provider-profiles/7/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels[0]").value("PHONE"));
    }

    @Test
    void advertisementChannels_listsSetChannels() throws Exception {
        when(contactRevealService.availableChannels(EntityType.ADVERTISEMENT, 5L)).thenReturn(List.of(ContactChannel.TELEGRAM));

        mockMvc.perform(get("/api/advertisements/5/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels[0]").value("TELEGRAM"));
    }

    // ── reveal (public, bearer key optional) ─────────────────────────────────

    @Test
    void reveal_anonymous_passesNullViewerAndRemoteAddress() throws Exception {
        when(contactRevealService.reveal(EntityType.PROVIDER_PROFILE, PROFILE_ID, ContactChannel.PHONE, null, "127.0.0.1"))
                .thenReturn(Optional.of(new ContactRevealService.RevealedContact(ContactChannel.PHONE, "+380501234567", null)));

        mockMvc.perform(post("/api/provider-profiles/7/contact/reveals")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"channel":"PHONE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("PHONE"))
                .andExpect(jsonPath("$.value").value("+380501234567"))
                .andExpect(jsonPath("$.deepLink").doesNotExist());
    }

    @Test
    void reveal_bearer_passesActorAsViewer() throws Exception {
        authenticateAs(ACTOR_ID);
        when(contactRevealService.reveal(EntityType.ADVERTISEMENT, 5L, ContactChannel.TELEGRAM, ACTOR_ID, "127.0.0.1"))
                .thenReturn(Optional.of(new ContactRevealService.RevealedContact(
                        ContactChannel.TELEGRAM, "ivan_master", "https://t.me/ivan_master")));

        mockMvc.perform(post("/api/advertisements/5/contact/reveals")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"channel":"TELEGRAM"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("ivan_master"))
                .andExpect(jsonPath("$.deepLink").value("https://t.me/ivan_master"));
    }

    @Test
    void reveal_channelNotSet_returnsNotFound() throws Exception {
        when(contactRevealService.reveal(EntityType.PROVIDER_PROFILE, PROFILE_ID, ContactChannel.VIBER, null, "127.0.0.1"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/provider-profiles/7/contact/reveals")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"channel":"VIBER"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void reveal_overLimit_returnsTooManyRequestsWithRetryAfter() throws Exception {
        when(contactRevealService.reveal(EntityType.PROVIDER_PROFILE, PROFILE_ID, ContactChannel.PHONE, null, "127.0.0.1"))
                .thenThrow(new TooManyAttemptsException("x", Duration.ofMinutes(15)));

        mockMvc.perform(post("/api/provider-profiles/7/contact/reveals")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"channel":"PHONE"}"""))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));
    }

    @Test
    void reveal_missingChannel_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/provider-profiles/7/contact/reveals")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── views (owner, administrator, moderator) ──────────────────────────────

    @Test
    void views_returnsCountsPerChannel() throws Exception {
        authenticateAs(ACTOR_ID);
        when(contactAccessService.countProfileViewsThisMonth(ACTOR_ID, PROFILE_ID))
                .thenReturn(List.of(new ContactViewCountDto(ContactChannel.PHONE, 3)));

        mockMvc.perform(get("/api/provider-profiles/7/contact/views"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].count").value(3));
    }

    @Test
    void views_unknownProfile_returnsNotFound() throws Exception {
        authenticateAs(ACTOR_ID);
        when(contactAccessService.countProfileViewsThisMonth(ACTOR_ID, PROFILE_ID))
                .thenThrow(new NoSuchElementException("Provider profile 7 not found"));

        mockMvc.perform(get("/api/provider-profiles/7/contact/views"))
                .andExpect(status().isNotFound());
    }

    @Test
    void views_notAllowed_returnsForbidden() throws Exception {
        authenticateAs(ACTOR_ID);
        when(contactAccessService.countProfileViewsThisMonth(ACTOR_ID, PROFILE_ID))
                .thenThrow(new AccessDeniedException("Not allowed"));

        mockMvc.perform(get("/api/provider-profiles/7/contact/views"))
                .andExpect(status().isForbidden());
    }
}
