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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private static UriComponentsBuilder baseUri() {
        return UriComponentsBuilder.fromUriString("http://localhost/api/provider-profiles");
    }

    @Test
    void list_delegatesToReadServiceAndSetsTotalCountHeader() {
        ProviderProfileDto info = ProviderProfileDto.builder().id(1L).build();
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.empty();
        when(readService.getFiltered(eq(filter), eq(0), eq(20), eq(Sort.unsorted()))).thenReturn(List.of(info));
        when(readService.count(eq(filter))).thenReturn(1);

        ResponseEntity<List<ProviderProfileDto>> result = controller.list(filter, 0, 20, null, baseUri());

        assertThat(result.getBody()).containsExactly(info);
        assertThat(result.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
    }

    @Test
    void list_withSortParam_parsesIntoSort() {
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.empty();
        when(readService.getFiltered(eq(filter), eq(0), eq(20), eq(Sort.by(Sort.Direction.DESC, "createdAt")))).thenReturn(List.of());
        when(readService.count(eq(filter))).thenReturn(0);

        controller.list(filter, 0, 20, "createdAt,desc", baseUri());

        verify(readService).getFiltered(eq(filter), eq(0), eq(20), eq(Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    void list_unknownSortField_throws() {
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.empty();

        assertThatThrownBy(() -> controller.list(filter, 0, 20, "secretField", baseUri()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_delegatesToSaveService() {
        controller.delete(ACTOR_ID, 1L, 0L);

        verify(saveService).delete(1L, ACTOR_ID, 0L);
    }
}
