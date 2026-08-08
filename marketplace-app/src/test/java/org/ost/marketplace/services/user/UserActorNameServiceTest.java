package org.ost.marketplace.services.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.orchestrator.services.ActorLookupService;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActorNameServiceTest {

    @Mock private ActorLookupService actorLookupService;
    @Mock private I18nService i18n;

    private UserActorNameService service;

    @BeforeEach
    void setUp() {
        service = new UserActorNameService(actorLookupService, i18n);
    }

    @Test
    void resolveNames_noDeletedActors_returnsNamesUnchanged() {
        when(actorLookupService.findActorNames(Set.of(1L, 2L))).thenReturn(Map.of(1L, "Alice", 2L, "Bob"));
        when(actorLookupService.findDeletedIds(Set.of(1L, 2L))).thenReturn(Set.of());

        Map<Long, String> result = service.resolveNames(Set.of(1L, 2L));

        assertThat(result).containsEntry(1L, "Alice").containsEntry(2L, "Bob");
    }

    @Test
    void resolveNames_deletedActor_appendsI18nSuffix() {
        when(actorLookupService.findActorNames(Set.of(1L, 2L))).thenReturn(Map.of(1L, "Alice", 2L, "Bob"));
        when(actorLookupService.findDeletedIds(Set.of(1L, 2L))).thenReturn(Set.of(2L));
        when(i18n.get(org.ost.marketplace.services.i18n.I18nKey.AUDIT_ACTOR_DELETED_NAME, "Bob"))
                .thenReturn("Bob (deleted)");

        Map<Long, String> result = service.resolveNames(Set.of(1L, 2L));

        assertThat(result).containsEntry(1L, "Alice").containsEntry(2L, "Bob (deleted)");
    }

    @Test
    void resolveNames_userStarterAbsent_returnsEmptyMap() {
        when(actorLookupService.findActorNames(Set.of(1L))).thenReturn(Map.of());
        when(actorLookupService.findDeletedIds(Set.of(1L))).thenReturn(Set.of());

        Map<Long, String> result = service.resolveNames(Set.of(1L));

        assertThat(result).isEmpty();
    }
}
