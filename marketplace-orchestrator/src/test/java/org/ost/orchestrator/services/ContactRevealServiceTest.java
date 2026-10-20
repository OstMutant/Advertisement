package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.TooManyAttemptsException;
import org.ost.platform.core.model.EntityType;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ContactRevealService}: channel lookup, deep links, view recording and the reveal rate limit. */
@ExtendWith(MockitoExtension.class)
class ContactRevealServiceTest {

    private static final EntityType ENTITY = EntityType.PROVIDER_PROFILE;
    private static final Long ID = 7L;
    private static final String PHONE_VALUE = "+380501234567";

    @Mock private ContactAccessService contactAccessService;

    private ContactRevealService service;

    @BeforeEach
    void setUp() {
        service = new ContactRevealService(contactAccessService);
    }

    @Test
    void availableChannels_returnsSetChannelsInFixedOrder() {
        when(contactAccessService.resolveContact(ENTITY, ID)).thenReturn(Optional.of(
                new ContactInfoDto(1L, ENTITY, ID, PHONE_VALUE, null, "+380509999999", null, 0L)));

        assertThat(service.availableChannels(ENTITY, ID)).containsExactly(ContactChannel.PHONE, ContactChannel.VIBER);
    }

    @Test
    void availableChannels_noContact_returnsEmpty() {
        when(contactAccessService.resolveContact(ENTITY, ID)).thenReturn(Optional.empty());

        assertThat(service.availableChannels(ENTITY, ID)).isEmpty();
    }

    @Test
    void reveal_phone_returnsValueWithoutDeepLink_andRecordsView() {
        when(contactAccessService.resolveContact(ENTITY, ID)).thenReturn(Optional.of(phoneOnly()));

        Optional<ContactRevealService.RevealedContact> revealed = service.reveal(ENTITY, ID, ContactChannel.PHONE, 5L, "10.0.0.1");

        assertThat(revealed).contains(new ContactRevealService.RevealedContact(ContactChannel.PHONE, PHONE_VALUE, null));
        verify(contactAccessService).recordView(ENTITY, ID, ContactChannel.PHONE, 5L, PHONE_VALUE);
    }

    @Test
    void reveal_telegram_buildsDeepLink() {
        when(contactAccessService.resolveContact(ENTITY, ID)).thenReturn(Optional.of(
                new ContactInfoDto(1L, ENTITY, ID, null, "ivan_master", null, null, 0L)));

        Optional<ContactRevealService.RevealedContact> revealed = service.reveal(ENTITY, ID, ContactChannel.TELEGRAM, 5L, "10.0.0.1");

        assertThat(revealed).hasValueSatisfying(contact -> assertThat(contact.deepLink()).isEqualTo("https://t.me/ivan_master"));
    }

    @Test
    void reveal_viber_buildsDeepLink() {
        when(contactAccessService.resolveContact(ENTITY, ID)).thenReturn(Optional.of(
                new ContactInfoDto(1L, ENTITY, ID, null, null, PHONE_VALUE, null, 0L)));

        Optional<ContactRevealService.RevealedContact> revealed = service.reveal(ENTITY, ID, ContactChannel.VIBER, 5L, "10.0.0.1");

        assertThat(revealed).hasValueSatisfying(contact ->
                assertThat(contact.deepLink()).isEqualTo("viber://chat?number=+380501234567"));
    }

    @Test
    void reveal_channelNotSet_returnsEmpty_andRecordsNothing() {
        when(contactAccessService.resolveContact(ENTITY, ID)).thenReturn(Optional.of(phoneOnly()));

        Optional<ContactRevealService.RevealedContact> revealed = service.reveal(ENTITY, ID, ContactChannel.TELEGRAM, 5L, "10.0.0.1");

        assertThat(revealed).isEmpty();
        verify(contactAccessService, never()).recordView(any(), any(), any(), any(), any());
    }

    @Test
    void reveal_visitor_recordsViewWithNullViewer() {
        when(contactAccessService.resolveContact(ENTITY, ID)).thenReturn(Optional.of(phoneOnly()));

        service.reveal(ENTITY, ID, ContactChannel.PHONE, null, "10.0.0.1");

        verify(contactAccessService).recordView(ENTITY, ID, ContactChannel.PHONE, null, PHONE_VALUE);
    }

    @Test
    void reveal_overLimit_throwsTooManyAttempts() {
        when(contactAccessService.resolveContact(ENTITY, ID)).thenReturn(Optional.of(phoneOnly()));
        for (int i = 0; i < 30; i++) {
            service.reveal(ENTITY, ID, ContactChannel.PHONE, null, "10.0.0.1");
        }

        assertThatThrownBy(() -> service.reveal(ENTITY, ID, ContactChannel.PHONE, null, "10.0.0.1"))
                .isInstanceOf(TooManyAttemptsException.class)
                .satisfies(ex -> assertThat(((TooManyAttemptsException) ex).retryAfter()).contains(Duration.ofSeconds(900)));
    }

    @Test
    void reveal_limitIsPerAddressAndViewer() {
        when(contactAccessService.resolveContact(ENTITY, ID)).thenReturn(Optional.of(phoneOnly()));
        for (int i = 0; i < 30; i++) {
            service.reveal(ENTITY, ID, ContactChannel.PHONE, null, "a");
        }

        assertThat(service.reveal(ENTITY, ID, ContactChannel.PHONE, null, "b")).isPresent();
        assertThat(service.reveal(ENTITY, ID, ContactChannel.PHONE, 7L, "a")).isPresent();
    }

    private static ContactInfoDto phoneOnly() {
        return new ContactInfoDto(1L, ENTITY, ID, PHONE_VALUE, null, null, null, 0L);
    }
}
