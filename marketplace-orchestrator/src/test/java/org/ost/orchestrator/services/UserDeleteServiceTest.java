package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.ost.platform.user.spi.UserAccountPort;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeleteServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long ACTOR_ID = 10L;

    @Mock private ComponentFactory<AdvertisementPort> advertisementPortFactory;
    @Mock private AdvertisementPort advertisementPort;
    @Mock private ComponentFactory<ProviderProfilePort> providerProfilePortFactory;
    @Mock private ProviderProfilePort providerProfilePort;
    @Mock private AdvertisementSaveService advertisementSaveService;
    @Mock private UserAccountPort accountPort;

    private UserDeleteService service;

    @BeforeEach
    void setUp() {
        service = new UserDeleteService(advertisementPortFactory, providerProfilePortFactory, advertisementSaveService, accountPort);
    }

    private void stubAdvertisementPortAvailable() {
        lenient().doAnswer(inv -> {
            Consumer<AdvertisementPort> consumer = inv.getArgument(0);
            consumer.accept(advertisementPort);
            return null;
        }).when(advertisementPortFactory).ifAvailable(any());
    }

    private void stubProviderProfilePortAvailable() {
        lenient().doAnswer(inv -> {
            Consumer<ProviderProfilePort> consumer = inv.getArgument(0);
            consumer.accept(providerProfilePort);
            return null;
        }).when(providerProfilePortFactory).ifAvailable(any());
    }

    @Test
    void delete_userWithAds_deletesEachAdBeforeTheUser() {
        stubAdvertisementPortAvailable();
        AdvertisementInfoDto ad1 = AdvertisementInfoDto.builder().id(1L).version(1L).build();
        AdvertisementInfoDto ad2 = AdvertisementInfoDto.builder().id(2L).version(3L).build();
        when(advertisementPort.findByCreator(USER_ID)).thenReturn(List.of(ad1, ad2));

        service.delete(USER_ID, ACTOR_ID);

        InOrder order = inOrder(advertisementSaveService, accountPort);
        order.verify(advertisementSaveService).delete(1L, ACTOR_ID, 1L);
        order.verify(advertisementSaveService).delete(2L, ACTOR_ID, 3L);
        order.verify(accountPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_userWithNoAds_onlyDeletesTheUser() {
        stubAdvertisementPortAvailable();
        when(advertisementPort.findByCreator(USER_ID)).thenReturn(List.of());

        service.delete(USER_ID, ACTOR_ID);

        verify(advertisementSaveService, never()).delete(any(), any(), any());
        verify(accountPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_advertisementStarterAbsent_stillDeletesTheUser() {
        // advertisementPortFactory.ifAvailable(...) left unstubbed -- ObjectProvider-absent shape.
        service.delete(USER_ID, ACTOR_ID);

        verify(accountPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_userWithProviderProfile_deletesProfileBeforeTheUser() {
        stubProviderProfilePortAvailable();
        ProviderProfileDto profile = ProviderProfileDto.builder().id(7L).version(2L).build();
        when(providerProfilePort.findByActorId(USER_ID)).thenReturn(Optional.of(profile));

        service.delete(USER_ID, ACTOR_ID);

        InOrder order = inOrder(providerProfilePort, accountPort);
        order.verify(providerProfilePort).delete(7L, 2L);
        order.verify(accountPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_userWithNoProviderProfile_doesNotCallDelete() {
        stubProviderProfilePortAvailable();
        when(providerProfilePort.findByActorId(USER_ID)).thenReturn(Optional.empty());

        service.delete(USER_ID, ACTOR_ID);

        verify(providerProfilePort, never()).delete(any(), any());
        verify(accountPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_providerProfileStarterAbsent_stillDeletesTheUser() {
        // providerProfilePortFactory.ifAvailable(...) left unstubbed -- ObjectProvider-absent shape.
        service.delete(USER_ID, ACTOR_ID);

        verify(accountPort).delete(USER_ID, ACTOR_ID);
    }
}
