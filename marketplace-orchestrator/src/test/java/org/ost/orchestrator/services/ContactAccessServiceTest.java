package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.contact.spi.ContactPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain Mockito unit test for {@link ContactService}, focused on {@link ContactService#resolveContact}'s fallback branching. */
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock private ComponentFactory<ContactPort> contactPortFactory;
    @Mock private ContactPort contactPort;
    @Mock private AdvertisementOwnerProfileLookupService advertisementOwnerProfileLookupService;

    private ContactService service;

    private static final ContactInfoDto AD_OWN_CONTACT = new ContactInfoDto(
            1L, EntityType.ADVERTISEMENT, 10L, "+380501111111", null, null, null, 0L);
    private static final ContactInfoDto PROFILE_CONTACT = new ContactInfoDto(
            2L, EntityType.PROVIDER_PROFILE, 99L, "+380502222222", null, null, null, 0L);

    @BeforeEach
    void setUp() {
        service = new ContactService(contactPortFactory, advertisementOwnerProfileLookupService);
        lenient().when(contactPortFactory.findIfAvailable()).thenReturn(Optional.of(contactPort));
        lenient().doAnswer(inv -> {
            Consumer<ContactPort> consumer = inv.getArgument(0);
            consumer.accept(contactPort);
            return null;
        }).when(contactPortFactory).ifAvailable(any());
    }

    @Test
    void resolveContact_advertisementHasOwnContact_returnsItWithoutFallback() {
        when(contactPort.find(EntityType.ADVERTISEMENT, 10L)).thenReturn(Optional.of(AD_OWN_CONTACT));

        assertThat(service.resolveContact(EntityType.ADVERTISEMENT, 10L)).contains(AD_OWN_CONTACT);
        verify(advertisementOwnerProfileLookupService, never()).findOwnerProfileId(any());
    }

    @Test
    void resolveContact_advertisementNoOwnContact_fallsBackToOwnerProfile() {
        when(contactPort.find(EntityType.ADVERTISEMENT, 10L)).thenReturn(Optional.empty());
        when(advertisementOwnerProfileLookupService.findOwnerProfileId(10L)).thenReturn(Optional.of(99L));
        when(contactPort.find(EntityType.PROVIDER_PROFILE, 99L)).thenReturn(Optional.of(PROFILE_CONTACT));

        assertThat(service.resolveContact(EntityType.ADVERTISEMENT, 10L)).contains(PROFILE_CONTACT);
    }

    @Test
    void resolveContact_advertisementNoOwnContactAndNoOwnerProfile_returnsEmpty() {
        when(contactPort.find(EntityType.ADVERTISEMENT, 10L)).thenReturn(Optional.empty());
        when(advertisementOwnerProfileLookupService.findOwnerProfileId(10L)).thenReturn(Optional.empty());

        assertThat(service.resolveContact(EntityType.ADVERTISEMENT, 10L)).isEmpty();
    }

    @Test
    void resolveContact_providerProfile_neverUsesFallback() {
        when(contactPort.find(EntityType.PROVIDER_PROFILE, 99L)).thenReturn(Optional.of(PROFILE_CONTACT));

        assertThat(service.resolveContact(EntityType.PROVIDER_PROFILE, 99L)).contains(PROFILE_CONTACT);
        verify(advertisementOwnerProfileLookupService, never()).findOwnerProfileId(any());
    }

    @Test
    void resolveContact_contactStarterAbsent_returnsEmpty() {
        when(contactPortFactory.findIfAvailable()).thenReturn(Optional.empty());

        assertThat(service.resolveContact(EntityType.PROVIDER_PROFILE, 99L)).isEmpty();
    }

    @Test
    void recordView_delegatesToPort() {
        service.recordView(EntityType.ADVERTISEMENT, 10L, ContactChannel.PHONE, 5L);

        verify(contactPort).recordView(EntityType.ADVERTISEMENT, 10L, ContactChannel.PHONE, 5L);
    }

    @Test
    void recordView_contactStarterAbsent_doesNothing() {
        doNothing().when(contactPortFactory).ifAvailable(any()); // overrides setUp()'s "always invoke" stub -- ObjectProvider-absent shape.

        service.recordView(EntityType.ADVERTISEMENT, 10L, ContactChannel.PHONE, 5L);

        verify(contactPort, never()).recordView(any(), any(), any(), any());
    }

    @Test
    void isAvailable_reflectsPortFactory() {
        assertThat(service.isAvailable()).isTrue();
    }
}
