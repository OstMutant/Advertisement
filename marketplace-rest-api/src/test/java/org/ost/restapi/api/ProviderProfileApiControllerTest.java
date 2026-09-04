package org.ost.restapi.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.model.ProviderKind;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderProfileApiControllerTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private ProviderProfileSaveService saveService;
    @Mock private ProviderProfileReadService readService;

    private ProviderProfileApiController controller;

    @BeforeEach
    void setUp() {
        controller = new ProviderProfileApiController(saveService, readService);
    }

    @Test
    void create_targetUserIdAlwaysEqualsActorId() {
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, ProviderKind.MASTER, "About", Set.of(), null, null);
        ProviderProfileDto saved = ProviderProfileDto.builder().id(1L).build();
        when(saveService.save(dto, ACTOR_ID, ACTOR_ID)).thenReturn(1L);
        when(readService.findById(1L)).thenReturn(Optional.of(saved));

        ProviderProfileDto result = controller.create(ACTOR_ID, dto);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void list_delegatesToReadService() {
        ProviderProfileDto info = ProviderProfileDto.builder().id(1L).build();
        when(readService.getFiltered(eq(ProviderProfileFilterDto.empty()), eq(0), eq(20), any())).thenReturn(List.of(info));

        assertThat(controller.list(0, 20)).containsExactly(info);
    }

    @Test
    void delete_delegatesToSaveService() {
        controller.delete(ACTOR_ID, 1L, 0L);

        verify(saveService).delete(1L, ACTOR_ID, 0L);
    }
}
