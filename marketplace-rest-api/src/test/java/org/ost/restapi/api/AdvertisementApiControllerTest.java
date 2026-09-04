package org.ost.restapi.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AdvertisementReadService;
import org.ost.orchestrator.services.AdvertisementSaveService;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.model.AdKind;
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
class AdvertisementApiControllerTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private AdvertisementSaveService saveService;
    @Mock private AdvertisementReadService readService;

    private AdvertisementApiController controller;

    @BeforeEach
    void setUp() {
        controller = new AdvertisementApiController(saveService, readService);
    }

    @Test
    void create_savesThenReturnsFreshCopy() {
        AdvertisementSaveDto dto = new AdvertisementSaveDto(null, "Title", "Desc", AdKind.OFFER, Set.of(1L), null, null);
        AdvertisementInfoDto saved = AdvertisementInfoDto.builder().id(100L).title("Title").build();
        when(saveService.save(eq(dto), eq(ACTOR_ID), any())).thenReturn(100L);
        when(readService.findById(100L)).thenReturn(Optional.of(saved));

        AdvertisementInfoDto result = controller.create(ACTOR_ID, dto);

        assertThat(result).isEqualTo(saved);
    }

    private static UriComponentsBuilder baseUri() {
        return UriComponentsBuilder.fromUriString("http://localhost/api/advertisements");
    }

    @Test
    void list_delegatesToReadServiceAndSetsTotalCountHeader() {
        AdvertisementInfoDto info = AdvertisementInfoDto.builder().id(1L).build();
        AdvertisementFilterDto filter = AdvertisementFilterDto.empty();
        when(readService.getFiltered(eq(filter), eq(0), eq(20), eq(Sort.unsorted()))).thenReturn(List.of(info));
        when(readService.count(eq(filter))).thenReturn(1);

        ResponseEntity<List<AdvertisementInfoDto>> result = controller.list(filter, 0, 20, null, baseUri());

        assertThat(result.getBody()).containsExactly(info);
        assertThat(result.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(result.getHeaders().get(HttpHeaders.LINK)).isNull();
    }

    @Test
    void list_withSortParam_parsesIntoSort() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.empty();
        when(readService.getFiltered(eq(filter), eq(0), eq(20), eq(Sort.by(Sort.Direction.DESC, "createdAt")))).thenReturn(List.of());
        when(readService.count(eq(filter))).thenReturn(0);

        controller.list(filter, 0, 20, "createdAt,desc", baseUri());

        verify(readService).getFiltered(eq(filter), eq(0), eq(20), eq(Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    void list_unknownSortField_throws() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.empty();

        assertThatThrownBy(() -> controller.list(filter, 0, 20, "secretField", baseUri()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void list_notLastPage_setsLinkHeaderWithNext() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.empty();
        when(readService.getFiltered(eq(filter), eq(0), eq(20), any())).thenReturn(List.of());
        when(readService.count(eq(filter))).thenReturn(45);

        ResponseEntity<List<AdvertisementInfoDto>> result = controller.list(filter, 0, 20, null, baseUri());

        assertThat(result.getHeaders().getFirst(HttpHeaders.LINK)).contains("rel=\"next\"");
    }

    @Test
    void getById_found_returnsIt() {
        AdvertisementInfoDto info = AdvertisementInfoDto.builder().id(1L).build();
        when(readService.findById(1L)).thenReturn(Optional.of(info));

        assertThat(controller.getById(1L)).isEqualTo(info);
    }

    @Test
    void getById_notFound_throws() {
        when(readService.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getById(1L)).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void update_pathIdWinsOverBodyId() {
        AdvertisementSaveDto dto = new AdvertisementSaveDto(999L, "Title", "Desc", AdKind.OFFER, Set.of(), null, 0L);
        AdvertisementInfoDto saved = AdvertisementInfoDto.builder().id(1L).build();
        when(saveService.save(any(), eq(ACTOR_ID), any())).thenReturn(1L);
        when(readService.findById(1L)).thenReturn(Optional.of(saved));

        controller.update(ACTOR_ID, 1L, dto);

        verify(saveService).save(eq(new AdvertisementSaveDto(1L, "Title", "Desc", AdKind.OFFER, Set.of(), null, 0L)), eq(ACTOR_ID), any());
    }

    @Test
    void delete_delegatesToSaveService() {
        controller.delete(ACTOR_ID, 1L, 0L);

        verify(saveService).delete(1L, ACTOR_ID, 0L);
    }
}
