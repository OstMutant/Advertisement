package org.ost.integrationtests.level1.providerprofile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.integrationtests.support.ProviderProfileServiceTestSupport;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.model.SqlPredicate;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileScope;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.provider.entity.ProviderProfile;
import org.ost.provider.repository.ProviderProfileRepository;
import org.ost.provider.services.ProviderProfileService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mockito-based (no Spring context, no Testcontainers) tests for {@link ProviderProfileService} — SUPPORT-privilege auth, HTML sanitization, and TaxonPort filter resolution. */
@ExtendWith(MockitoExtension.class)
class ProviderProfileServiceTest {

    @Mock
    private ProviderProfileRepository repository;
    @Mock
    private ComponentFactory<TaxonPort> taxonPortFactory;

    private ProviderProfileService newService() {
        return ProviderProfileServiceTestSupport.newService(repository, taxonPortFactory);
    }

    @Test
    void save_supportKind_notPrivileged_throwsIllegalStateException() {
        ProviderProfileService service = newService();
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, ProviderKind.SUPPORT, "About", null, null, null, null, null, null);

        assertThatThrownBy(() -> service.save(dto, 1L, 1L, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPPORT");
    }

    @Test
    void save_supportKind_privileged_succeeds() {
        ProviderProfileService service = newService();
        when(repository.save(any()))
                .thenReturn(ProviderProfile.builder().id(1L).build());
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, ProviderKind.SUPPORT, "About", null, null, null, null, null, null);

        Long id = service.save(dto, 1L, 1L, true);

        assertThat(id).isEqualTo(1L);
    }

    @Test
    void save_masterKind_notPrivileged_succeeds() {
        ProviderProfileService service = newService();
        when(repository.save(any()))
                .thenReturn(ProviderProfile.builder().id(1L).build());
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, ProviderKind.MASTER, "About", null, null, null, null, null, null);

        Long id = service.save(dto, 1L, 1L, false);

        assertThat(id).isEqualTo(1L);
    }

    @Test
    void save_stripsDisallowedTags_keepsAllowedFormatting() {
        ProviderProfileService service = newService();
        ArgumentCaptor<ProviderProfile> captor = ArgumentCaptor.forClass(ProviderProfile.class);
        when(repository.save(captor.capture())).thenReturn(ProviderProfile.builder().id(1L).build());
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(
                null, ProviderKind.MASTER, "<script>alert(1)</script><b>Bold</b>", null, null, null, null, null, null);

        service.save(dto, 1L, 1L, false);

        assertThat(captor.getValue().getAbout())
                .doesNotContain("<script>")
                .contains("<b>Bold</b>");
    }

    @Test
    void save_aboutExceedsVisibleTextMaxLength_throws() {
        ProviderProfileService service = newService();
        String tooLong = "a".repeat(ProviderProfileSaveDto.ABOUT_MAX_LENGTH + 1);
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, ProviderKind.MASTER, tooLong, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.save(dto, 1L, 1L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void save_aboutAtVisibleTextMaxLength_succeeds() {
        ProviderProfileService service = newService();
        ArgumentCaptor<ProviderProfile> captor = ArgumentCaptor.forClass(ProviderProfile.class);
        when(repository.save(captor.capture())).thenReturn(ProviderProfile.builder().id(1L).build());
        String exactlyMax = "a".repeat(ProviderProfileSaveDto.ABOUT_MAX_LENGTH);
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, ProviderKind.MASTER, exactlyMax, null, null, null, null, null, null);

        service.save(dto, 1L, 1L, false);

        assertThat(captor.getValue().getAbout()).hasSize(ProviderProfileSaveDto.ABOUT_MAX_LENGTH);
    }

    @Test
    void save_actorIdTakenFromTargetUser_whenCreatingNewProfile() {
        ProviderProfileService service = newService();
        ArgumentCaptor<ProviderProfile> captor = ArgumentCaptor.forClass(ProviderProfile.class);
        when(repository.save(captor.capture())).thenReturn(ProviderProfile.builder().id(1L).build());
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, ProviderKind.MASTER, "About", null, null, null, null, null, null);

        service.save(dto, 42L, 99L, false);

        assertThat(captor.getValue().getActorId()).isEqualTo(42L);
    }

    @Test
    void getFiltered_taxonPredicate_passesTheScopeUnchangedAndThePredicate() {
        ProviderProfileService service = newService();
        TaxonPort taxonPort = mock(TaxonPort.class);
        SqlPredicate predicate = new SqlPredicate("pp.id = ANY(:_ids)", Map.of("_ids", new Long[]{20L}));
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.categoryAndCityPredicate(EntityType.PROVIDER_PROFILE, ProviderProfileRepository.ID, null, 7L))
                .thenReturn(Optional.of(predicate));
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.builder().cityTaxonId(7L).build();
        PageRequest pageable = PageRequest.of(0, 10, Sort.unsorted());
        ProviderProfileScope scope = new ProviderProfileScope(Set.of(10L, 20L), List.of(), false);

        service.getFiltered(filter, 0, 10, Sort.unsorted(), scope);

        verify(repository).findByFilter(eq(filter), eq(pageable), same(scope), same(predicate));
    }

    @Test
    void getFiltered_taxonPortAbsent_passesTheScopeUnchangedAndNullPredicate() {
        ProviderProfileService service = newService();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.empty());
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.builder().cityTaxonId(7L).build();
        PageRequest pageable = PageRequest.of(0, 10, Sort.unsorted());
        ProviderProfileScope scope = new ProviderProfileScope(Set.of(10L, 20L), List.of(), false);

        service.getFiltered(filter, 0, 10, Sort.unsorted(), scope);

        verify(repository).findByFilter(eq(filter), eq(pageable), same(scope), isNull());
    }

    @Test
    void getFiltered_portReturnsNoPredicate_passesTheScopeUnchangedAndNullPredicate() {
        ProviderProfileService service = newService();
        TaxonPort taxonPort = mock(TaxonPort.class);
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.categoryAndCityPredicate(EntityType.PROVIDER_PROFILE, ProviderProfileRepository.ID, null, 7L))
                .thenReturn(Optional.empty());
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.builder().cityTaxonId(7L).build();
        PageRequest pageable = PageRequest.of(0, 10, Sort.unsorted());
        ProviderProfileScope scope = new ProviderProfileScope(Set.of(10L, 20L), List.of(), false);

        service.getFiltered(filter, 0, 10, Sort.unsorted(), scope);

        verify(repository).findByFilter(eq(filter), eq(pageable), same(scope), isNull());
    }

    @Test
    void count_taxonPredicate_passesTheScopeUnchangedAndThePredicate() {
        ProviderProfileService service = newService();
        TaxonPort taxonPort = mock(TaxonPort.class);
        SqlPredicate predicate = new SqlPredicate("pp.id = ANY(:_ids)", Map.of("_ids", new Long[]{20L}));
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.categoryAndCityPredicate(EntityType.PROVIDER_PROFILE, ProviderProfileRepository.ID, null, 7L))
                .thenReturn(Optional.of(predicate));
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.builder().cityTaxonId(7L).build();
        ProviderProfileScope scope = new ProviderProfileScope(Set.of(10L, 20L), List.of(), false);
        when(repository.countByFilter(any(), any(), any())).thenReturn(1L);

        int count = service.count(filter, scope);

        assertThat(count).isEqualTo(1);
        verify(repository).countByFilter(eq(filter), same(scope), same(predicate));
    }

    @Test
    void count_taxonPortAbsent_passesTheScopeUnchangedAndNullPredicate() {
        ProviderProfileService service = newService();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.empty());
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.builder().cityTaxonId(7L).build();
        ProviderProfileScope scope = new ProviderProfileScope(Set.of(10L, 20L), List.of(), false);
        when(repository.countByFilter(any(), any(), any())).thenReturn(1L);

        int count = service.count(filter, scope);

        assertThat(count).isEqualTo(1);
        verify(repository).countByFilter(eq(filter), same(scope), isNull());
    }

    @Test
    void count_portReturnsNoPredicate_passesTheScopeUnchangedAndNullPredicate() {
        ProviderProfileService service = newService();
        TaxonPort taxonPort = mock(TaxonPort.class);
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.categoryAndCityPredicate(EntityType.PROVIDER_PROFILE, ProviderProfileRepository.ID, null, 7L))
                .thenReturn(Optional.empty());
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.builder().cityTaxonId(7L).build();
        ProviderProfileScope scope = new ProviderProfileScope(Set.of(10L, 20L), List.of(), false);
        when(repository.countByFilter(any(), any(), any())).thenReturn(1L);

        int count = service.count(filter, scope);

        assertThat(count).isEqualTo(1);
        verify(repository).countByFilter(eq(filter), same(scope), isNull());
    }
}
