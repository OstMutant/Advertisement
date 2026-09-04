package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.apikey.spi.ApiKeyPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.user.spi.UserAccountPort;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain Mockito unit test for {@link UserDeleteService}'s cascade-delete order. */
@ExtendWith(MockitoExtension.class)
class UserDeleteServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long ACTOR_ID = 10L;

    @Mock private ComponentFactory<AdvertisementPort> advertisementPortFactory;
    @Mock private AdvertisementPort advertisementPort;
    @Mock private ComponentFactory<ApiKeyPort> apiKeyPortFactory;
    @Mock private ApiKeyPort apiKeyPort;
    @Mock private AdvertisementSaveService advertisementSaveService;
    @Mock private ProviderProfileSaveService providerProfileSaveService;
    @Mock private UserAccountPort accountPort;
    @Mock private AuthorizationService authorizationService;

    private UserDeleteService service;

    @BeforeEach
    void setUp() {
        service = new UserDeleteService(advertisementPortFactory, apiKeyPortFactory, advertisementSaveService, providerProfileSaveService, accountPort, authorizationService);
    }

    private void stubAdvertisementPortAvailable() {
        lenient().doAnswer(inv -> {
            Consumer<AdvertisementPort> consumer = inv.getArgument(0);
            consumer.accept(advertisementPort);
            return null;
        }).when(advertisementPortFactory).ifAvailable(any());
    }

    private void stubApiKeyPortAvailable() {
        lenient().doAnswer(inv -> {
            Consumer<ApiKeyPort> consumer = inv.getArgument(0);
            consumer.accept(apiKeyPort);
            return null;
        }).when(apiKeyPortFactory).ifAvailable(any());
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
    void delete_userWithProviderProfile_deletesProfileThroughSaveServiceBeforeTheUser() {
        lenient().when(providerProfileSaveService.isAvailable()).thenReturn(true);
        ProviderProfileDto profile = ProviderProfileDto.builder().id(7L).version(2L).build();
        when(providerProfileSaveService.findByActorId(USER_ID)).thenReturn(Optional.of(profile));

        service.delete(USER_ID, ACTOR_ID);

        InOrder order = inOrder(providerProfileSaveService, accountPort);
        order.verify(providerProfileSaveService).delete(7L, ACTOR_ID, 2L);
        order.verify(accountPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_userWithNoProviderProfile_doesNotCallDelete() {
        lenient().when(providerProfileSaveService.isAvailable()).thenReturn(true);
        when(providerProfileSaveService.findByActorId(USER_ID)).thenReturn(Optional.empty());

        service.delete(USER_ID, ACTOR_ID);

        verify(providerProfileSaveService, never()).delete(any(), any(), any());
        verify(accountPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_providerProfileStarterAbsent_stillDeletesTheUser() {
        lenient().when(providerProfileSaveService.isAvailable()).thenReturn(false);
        service.delete(USER_ID, ACTOR_ID);

        verify(providerProfileSaveService, never()).findByActorId(any());
        verify(accountPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_userWithApiKeys_deletesKeysThroughPortBeforeTheUser() {
        stubApiKeyPortAvailable();

        service.delete(USER_ID, ACTOR_ID);

        InOrder order = inOrder(apiKeyPort, accountPort);
        order.verify(apiKeyPort).deleteAllForActor(USER_ID);
        order.verify(accountPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_apiKeyStarterAbsent_stillDeletesTheUser() {
        // apiKeyPortFactory.ifAvailable(...) left unstubbed -- ObjectProvider-absent shape.
        service.delete(USER_ID, ACTOR_ID);

        verify(accountPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_deniedByAuthorization_throwsAndNeverCascadesOrDeletes() {
        doThrow(new AccessDeniedException("denied")).when(authorizationService).requireCanEditAccount(ACTOR_ID, USER_ID);

        assertThatThrownBy(() -> service.delete(USER_ID, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(advertisementPortFactory, never()).ifAvailable(any());
        verify(apiKeyPortFactory, never()).ifAvailable(any());
        verify(accountPort, never()).delete(any(), any());
    }
}
