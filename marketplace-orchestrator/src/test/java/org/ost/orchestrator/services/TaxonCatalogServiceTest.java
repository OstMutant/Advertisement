package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.spi.TaxonPort;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TaxonCatalogService}'s write methods (create/update/softDelete/restore) have no
 * per-entry ownership concept -- privileged-only (admin or moderator), enforced via
 * {@link AuthorizationService#requireIsPrivileged}.
 */
@ExtendWith(MockitoExtension.class)
class TaxonCatalogServiceTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private ComponentFactory<TaxonPort> taxonPortFactory;
    @Mock private TaxonPort taxonPort;
    @Mock private AuthorizationService authorizationService;

    private TaxonCatalogService service;

    @BeforeEach
    void setUp() {
        service = new TaxonCatalogService(taxonPortFactory, authorizationService);
        lenient().when(taxonPortFactory.get()).thenReturn(taxonPort);
        lenient().doAnswer(inv -> {
            Consumer<TaxonPort> consumer = inv.getArgument(0);
            consumer.accept(taxonPort);
            return null;
        }).when(taxonPortFactory).ifAvailable(any());
    }

    @Test
    void create_checksAuthorizationBeforeCreating() {
        when(taxonPort.create(TaxonType.CATEGORY, Map.of(), ACTOR_ID)).thenReturn(1L);

        Long id = service.create(TaxonType.CATEGORY, Map.of(), ACTOR_ID);

        assertThat(id).isEqualTo(1L);
        InOrder order = inOrder(authorizationService, taxonPort);
        order.verify(authorizationService).requireIsPrivileged(ACTOR_ID);
        order.verify(taxonPort).create(TaxonType.CATEGORY, Map.of(), ACTOR_ID);
    }

    @Test
    void create_deniedByAuthorization_throwsAndNeverCreates() {
        doThrow(new AccessDeniedException("denied")).when(authorizationService).requireIsPrivileged(ACTOR_ID);

        assertThatThrownBy(() -> service.create(TaxonType.CATEGORY, Map.of(), ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(taxonPort, never()).create(any(), any(), any());
    }

    @Test
    void update_checksAuthorizationBeforeUpdating() {
        service.update(1L, Map.of(), ACTOR_ID, 5L);

        InOrder order = inOrder(authorizationService, taxonPort);
        order.verify(authorizationService).requireIsPrivileged(ACTOR_ID);
        order.verify(taxonPort).update(1L, Map.of(), ACTOR_ID, 5L);
    }

    @Test
    void update_deniedByAuthorization_throwsAndNeverUpdates() {
        doThrow(new AccessDeniedException("denied")).when(authorizationService).requireIsPrivileged(ACTOR_ID);

        assertThatThrownBy(() -> service.update(1L, Map.of(), ACTOR_ID, 5L))
                .isInstanceOf(AccessDeniedException.class);
        verify(taxonPort, never()).update(any(), any(), any(), any());
    }

    @Test
    void softDelete_checksAuthorizationBeforeDeleting() {
        service.softDelete(1L, ACTOR_ID, 5L);

        InOrder order = inOrder(authorizationService, taxonPort);
        order.verify(authorizationService).requireIsPrivileged(ACTOR_ID);
        order.verify(taxonPort).softDelete(1L, ACTOR_ID, 5L);
    }

    @Test
    void softDelete_deniedByAuthorization_throwsAndNeverDeletes() {
        doThrow(new AccessDeniedException("denied")).when(authorizationService).requireIsPrivileged(ACTOR_ID);

        assertThatThrownBy(() -> service.softDelete(1L, ACTOR_ID, 5L))
                .isInstanceOf(AccessDeniedException.class);
        verify(taxonPort, never()).softDelete(any(), any(), any());
    }

    @Test
    void restore_checksAuthorizationBeforeRestoring() {
        service.restore(1L, ACTOR_ID);

        InOrder order = inOrder(authorizationService, taxonPort);
        order.verify(authorizationService).requireIsPrivileged(ACTOR_ID);
        order.verify(taxonPort).restore(1L, ACTOR_ID);
    }

    @Test
    void restore_deniedByAuthorization_throwsAndNeverRestores() {
        doThrow(new AccessDeniedException("denied")).when(authorizationService).requireIsPrivileged(ACTOR_ID);

        assertThatThrownBy(() -> service.restore(1L, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(taxonPort, never()).restore(any(), any());
    }
}
