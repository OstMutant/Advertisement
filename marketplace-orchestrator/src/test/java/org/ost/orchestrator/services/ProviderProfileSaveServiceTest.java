package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.spi.CurrentLocaleHook;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * {@link ProviderProfileSaveService#save} orchestrates the create/update transaction: it decides
 * {@code captureCreation} vs {@code captureUpdate} and skips audit capture instead of throwing
 * when the profile was concurrently deleted mid-edit (same {@code before == null} shape
 * {@link AdvertisementSaveServiceTest} covers for advertisements).
 */
@ExtendWith(MockitoExtension.class)
class ProviderProfileSaveServiceTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private TransactionTemplate tx;
    @Mock private ComponentFactory<ProviderProfilePort> providerProfilePortFactory;
    @Mock private ComponentFactory<AuditPort> auditPortFactory;
    @Mock private ProviderProfilePort providerProfilePort;
    @Mock private AuditPort auditPort;
    @Mock private TaxonAssignmentWriteService taxonAssignmentWriteService;
    @Mock private ProviderProfileDisplayEnrichmentService displayEnrichmentService;
    @Mock private CurrentLocaleHook currentLocaleHook;

    private ProviderProfileSaveService service;

    @BeforeEach
    void setUp() {
        service = new ProviderProfileSaveService(tx, providerProfilePortFactory, auditPortFactory,
                taxonAssignmentWriteService, displayEnrichmentService, currentLocaleHook);
        lenient().when(tx.execute(this.<Long>callback())).thenAnswer(inv -> {
            TransactionCallback<Long> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        lenient().doAnswer(inv -> {
            Consumer<TransactionStatus> callback = inv.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
        lenient().when(providerProfilePortFactory.get()).thenReturn(providerProfilePort);
        lenient().when(currentLocaleHook.getCurrentLocale()).thenReturn(Locale.ENGLISH);
        lenient().when(displayEnrichmentService.enrichWithCategoryAndCity(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    private <T> TransactionCallback<T> callback() {
        return any(TransactionCallback.class);
    }

    private static <T> void stubAvailable(ComponentFactory<T> factory, T component) {
        lenient().when(factory.findIfAvailable()).thenReturn(Optional.of(component));
        lenient().doAnswer(inv -> {
            Consumer<T> consumer = inv.getArgument(0);
            consumer.accept(component);
            return null;
        }).when(factory).ifAvailable(any());
    }

    @Test
    void save_newProfile_capturesCreationNotUpdate() {
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, ProviderKind.MASTER, "About", Set.of(1L, 2L), 5L, null);
        when(providerProfilePort.save(dto, ACTOR_ID, ACTOR_ID, false)).thenReturn(100L);
        when(providerProfilePort.findById(100L)).thenReturn(Optional.of(
                ProviderProfileDto.builder().id(100L).kind(ProviderKind.MASTER).about("About")
                        .categoryIds(Set.of(1L, 2L)).cityTaxonId(5L).build()));
        stubAvailable(auditPortFactory, auditPort);

        Long id = service.save(dto, ACTOR_ID, ACTOR_ID, false);

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<AuditableSnapshot> afterCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureCreation(eq(100L), afterCaptor.capture(), eq(ACTOR_ID));
        verify(auditPort, never()).captureUpdate(any(), any(), any());
        ProviderProfileSnapshotDto after = (ProviderProfileSnapshotDto) afterCaptor.getValue();
        assertThat(after.about()).isEqualTo("About");
        assertThat(after.categoryIds()).containsExactly(1L, 2L);
        verify(taxonAssignmentWriteService).replace(EntityType.PROVIDER_PROFILE, 100L, Set.of(1L, 2L));
    }

    @Test
    void save_existingProfile_capturesUpdateWithAfter() {
        Long profileId = 42L;
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(profileId, ProviderKind.SHOP, "New about", Set.of(3L), 7L, 1L);
        ProviderProfileDto before = ProviderProfileDto.builder().id(profileId).kind(ProviderKind.MASTER).about("Old about").build();
        ProviderProfileDto after = ProviderProfileDto.builder().id(profileId).kind(ProviderKind.SHOP).about("New about")
                .categoryIds(Set.of(3L)).cityTaxonId(7L).build();

        when(providerProfilePort.findById(profileId)).thenReturn(Optional.of(before), Optional.of(after));
        when(providerProfilePort.save(dto, ACTOR_ID, ACTOR_ID, false)).thenReturn(profileId);
        stubAvailable(auditPortFactory, auditPort);

        Long id = service.save(dto, ACTOR_ID, ACTOR_ID, false);

        assertThat(id).isEqualTo(profileId);
        ArgumentCaptor<AuditableSnapshot> afterCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureUpdate(eq(profileId), afterCaptor.capture(), eq(ACTOR_ID));
        verify(auditPort, never()).captureCreation(any(), any(), any());
        assertThat(((ProviderProfileSnapshotDto) afterCaptor.getValue()).about()).isEqualTo("New about");
    }

    @Test
    void save_existingProfileConcurrentlyDeleted_savesButSkipsAuditCaptureInsteadOfThrowing() {
        Long profileId = 42L;
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(profileId, ProviderKind.SHOP, "New about", Set.of(), null, 1L);
        ProviderProfileDto after = ProviderProfileDto.builder().id(profileId).kind(ProviderKind.SHOP).about("New about").build();

        when(providerProfilePort.findById(profileId)).thenReturn(Optional.empty(), Optional.of(after));
        when(providerProfilePort.save(dto, ACTOR_ID, ACTOR_ID, false)).thenReturn(profileId);
        stubAvailable(auditPortFactory, auditPort);

        Long id = service.save(dto, ACTOR_ID, ACTOR_ID, false);

        assertThat(id).isEqualTo(profileId);
        verify(auditPort, never()).captureUpdate(any(), any(), any());
        verify(auditPort, never()).captureCreation(any(), any(), any());
    }

    @Test
    void save_optionalAuditPortAbsent_completesWithoutException() {
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, ProviderKind.MASTER, "About", null, null, null);
        when(providerProfilePort.save(dto, ACTOR_ID, ACTOR_ID, false)).thenReturn(1L);
        when(providerProfilePort.findById(1L)).thenReturn(Optional.of(
                ProviderProfileDto.builder().id(1L).kind(ProviderKind.MASTER).about("About").build()));
        // auditPortFactory left unstubbed -- ObjectProvider-absent shape.

        Long id = service.save(dto, ACTOR_ID, ACTOR_ID, false);

        assertThat(id).isEqualTo(1L);
    }

    @Test
    void save_supportKindByPrivilegedActor_passesPrivilegedFlagThrough() {
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, ProviderKind.SUPPORT, "About", null, null, null);
        when(providerProfilePort.save(dto, ACTOR_ID, ACTOR_ID, true)).thenReturn(1L);
        when(providerProfilePort.findById(1L)).thenReturn(Optional.of(
                ProviderProfileDto.builder().id(1L).kind(ProviderKind.SUPPORT).about("About").build()));
        stubAvailable(auditPortFactory, auditPort);

        service.save(dto, ACTOR_ID, ACTOR_ID, true);

        verify(providerProfilePort).save(dto, ACTOR_ID, ACTOR_ID, true);
    }

    @Test
    void isAvailable_delegatesToPortFactory() {
        when(providerProfilePortFactory.findIfAvailable()).thenReturn(Optional.of(providerProfilePort));
        assertThat(service.isAvailable()).isTrue();

        when(providerProfilePortFactory.findIfAvailable()).thenReturn(Optional.empty());
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void delete_existingProfile_capturesDeletionWithSnapshotReadBeforeDeleting() {
        Long profileId = 42L;
        Long version = 5L;
        when(providerProfilePort.findById(profileId)).thenReturn(Optional.of(
                ProviderProfileDto.builder().id(profileId).kind(ProviderKind.MASTER).about("Deleted about").build()));
        stubAvailable(auditPortFactory, auditPort);

        service.delete(profileId, ACTOR_ID, version);

        verify(providerProfilePort).delete(profileId, version);
        verify(taxonAssignmentWriteService).clear(EntityType.PROVIDER_PROFILE, profileId);
        ArgumentCaptor<AuditableSnapshot> snapshotCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureDeletion(eq(profileId), snapshotCaptor.capture(), eq(ACTOR_ID));
        assertThat(((ProviderProfileSnapshotDto) snapshotCaptor.getValue()).about()).isEqualTo("Deleted about");
    }

    @Test
    void delete_nonExistentProfile_stillCallsPortButNeverCascadesOrCapturesDeletion() {
        Long profileId = 99L;
        when(providerProfilePort.findById(profileId)).thenReturn(Optional.empty());
        stubAvailable(auditPortFactory, auditPort);

        service.delete(profileId, ACTOR_ID, null);

        verify(providerProfilePort).delete(profileId, null);
        verify(taxonAssignmentWriteService, never()).clear(any(), any());
        verify(auditPort, never()).captureDeletion(any(), any(), any());
    }

    @Test
    void delete_optionalAuditPortAbsent_completesWithoutException() {
        Long profileId = 42L;
        when(providerProfilePort.findById(profileId)).thenReturn(Optional.of(
                ProviderProfileDto.builder().id(profileId).kind(ProviderKind.MASTER).about("About").build()));
        // auditPortFactory left unstubbed -- ObjectProvider-absent shape.

        service.delete(profileId, ACTOR_ID, 1L);

        verify(providerProfilePort).delete(profileId, 1L);
    }
}
