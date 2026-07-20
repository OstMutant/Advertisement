package org.ost.marketplace.services.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.marketplace.services.advertisement.AdvertisementSaveService;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.spi.UserPort;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
    @Mock private AdvertisementSaveService advertisementSaveService;
    @Mock private UserPort userPort;

    private UserDeleteService service;

    @BeforeEach
    void setUp() {
        service = new UserDeleteService(advertisementPortFactory, advertisementSaveService, userPort);
    }

    @SuppressWarnings("unchecked")
    private void stubAdvertisementPortAvailable() {
        lenient().doAnswer(inv -> {
            Consumer<AdvertisementPort> consumer = inv.getArgument(0);
            consumer.accept(advertisementPort);
            return null;
        }).when(advertisementPortFactory).ifAvailable(any());
    }

    @Test
    void delete_userWithAds_deletesEachAdBeforeTheUser() {
        stubAdvertisementPortAvailable();
        AdvertisementInfoDto ad1 = AdvertisementInfoDto.builder().id(1L).version(1L).build();
        AdvertisementInfoDto ad2 = AdvertisementInfoDto.builder().id(2L).version(3L).build();
        when(advertisementPort.findByCreator(USER_ID)).thenReturn(List.of(ad1, ad2));

        service.delete(USER_ID, ACTOR_ID);

        InOrder order = inOrder(advertisementSaveService, userPort);
        order.verify(advertisementSaveService).delete(1L, ACTOR_ID, 1L);
        order.verify(advertisementSaveService).delete(2L, ACTOR_ID, 3L);
        order.verify(userPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_userWithNoAds_onlyDeletesTheUser() {
        stubAdvertisementPortAvailable();
        when(advertisementPort.findByCreator(USER_ID)).thenReturn(List.of());

        service.delete(USER_ID, ACTOR_ID);

        verify(advertisementSaveService, never()).delete(any(), any(), any());
        verify(userPort).delete(USER_ID, ACTOR_ID);
    }

    @Test
    void delete_advertisementStarterAbsent_stillDeletesTheUser() {
        // advertisementPortFactory.ifAvailable(...) left unstubbed -- ObjectProvider-absent shape.
        service.delete(USER_ID, ACTOR_ID);

        verify(userPort).delete(USER_ID, ACTOR_ID);
    }
}
