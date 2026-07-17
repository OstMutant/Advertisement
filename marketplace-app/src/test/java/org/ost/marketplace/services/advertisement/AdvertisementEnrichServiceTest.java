package org.ost.marketplace.services.advertisement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.spi.TaxonPort;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link AdvertisementEnrichService} merges attachment-hook media changes into audit
 * timeline/activity entries and resolves raw category ids into names. Both optional ports
 * ({@link TaxonPort}, {@link AttachmentAuditHook}) must degrade gracefully when absent
 * (improvement-048).
 */
@ExtendWith(MockitoExtension.class)
class AdvertisementEnrichServiceTest {

    @Mock private ComponentFactory<AttachmentAuditHook> attachmentAuditHookFactory;
    @Mock private ComponentFactory<TaxonPort> taxonPortFactory;
    @Mock private AttachmentAuditHook attachmentAuditHook;
    @Mock private TaxonPort taxonPort;

    private AdvertisementEnrichService service;

    @BeforeEach
    void setUp() {
        service = new AdvertisementEnrichService(attachmentAuditHookFactory, taxonPortFactory);
    }

    private static <T> void stubAvailable(ComponentFactory<T> factory, T component) {
        lenient().when(factory.findIfAvailable()).thenReturn(Optional.of(component));
        lenient().doAnswer(inv -> {
            Consumer<T> consumer = inv.getArgument(0);
            consumer.accept(component);
            return null;
        }).when(factory).ifAvailable(any());
    }

    private static TaxonDto taxon(Long id, String name) {
        return TaxonDto.builder().id(id).type(TaxonType.CATEGORY).name(name).description("").build();
    }

    private static AdvertisementSnapshotDto snapshot(List<Long> categoryIds, Long attachmentSnapshotId) {
        return new AdvertisementSnapshotDto("Title", "Desc", categoryIds, attachmentSnapshotId);
    }

    // ── mergeMediaChanges() (Timeline tab) ──────────────────────────────────────────────────

    @Test
    void mergeMediaChanges_mediaHookAvailable_mediaChangesPrecedeResolvedCategoryChanges() {
        stubAvailable(attachmentAuditHookFactory, attachmentAuditHook);
        stubAvailable(taxonPortFactory, taxonPort);
        when(taxonPort.findByIds(Set.of(1L), Locale.ENGLISH)).thenReturn(Map.of(1L, taxon(1L, "Electronics")));
        when(attachmentAuditHook.getChangesBySnapshotId(50L))
                .thenReturn(List.of(new ChangeEntry.MediaChange("old.jpg", "new.jpg")));

        AuditTimelineItemDto<AdvertisementSnapshotDto> item = new AuditTimelineItemDto<>(
                1L, new EntityRef(EntityType.ADVERTISEMENT, 1L), ActionType.UPDATED, null,
                List.of(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.categoryIds, "", "1")),
                10L, snapshot(List.of(1L), 50L), null);

        List<AuditTimelineItemDto<AdvertisementSnapshotDto>> result = service.mergeMediaChanges(List.of(item));

        List<ChangeEntry> changes = result.get(0).changes();
        assertThat(changes).hasSize(2);
        assertThat(changes.get(0)).isInstanceOf(ChangeEntry.MediaChange.class);
        assertThat(((ChangeEntry.FieldChange) changes.get(1)).to()).isEqualTo("Electronics");
    }

    @Test
    void mergeMediaChanges_nonAdvertisementEntity_passesThroughUnchanged() {
        AuditTimelineItemDto<AdvertisementSnapshotDto> item = new AuditTimelineItemDto<>(
                1L, new EntityRef(EntityType.USER, 1L), ActionType.UPDATED, null,
                List.of(), 10L, null, null);

        List<AuditTimelineItemDto<AdvertisementSnapshotDto>> result = service.mergeMediaChanges(List.of(item));

        assertThat(result.get(0)).isSameAs(item);
    }

    @Test
    void mergeMediaChanges_hookAbsent_noMediaChangesAddedButCategoriesStillResolved() {
        stubAvailable(taxonPortFactory, taxonPort);
        when(taxonPort.findByIds(Set.of(1L), Locale.ENGLISH)).thenReturn(Map.of(1L, taxon(1L, "Electronics")));

        AuditTimelineItemDto<AdvertisementSnapshotDto> item = new AuditTimelineItemDto<>(
                1L, new EntityRef(EntityType.ADVERTISEMENT, 1L), ActionType.UPDATED, null,
                List.of(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.categoryIds, "", "1")),
                10L, snapshot(List.of(1L), 50L), null);

        List<AuditTimelineItemDto<AdvertisementSnapshotDto>> result = service.mergeMediaChanges(List.of(item));

        List<ChangeEntry> changes = result.get(0).changes();
        assertThat(changes).hasSize(1);
        assertThat(((ChangeEntry.FieldChange) changes.get(0)).to()).isEqualTo("Electronics");
    }

    @Test
    void mergeMediaChanges_taxonPortAbsent_fallsBackToRawIdString() {
        AuditTimelineItemDto<AdvertisementSnapshotDto> item = new AuditTimelineItemDto<>(
                1L, new EntityRef(EntityType.ADVERTISEMENT, 1L), ActionType.UPDATED, null,
                List.of(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.categoryIds, "", "1")),
                10L, snapshot(List.of(1L), null), null);

        List<AuditTimelineItemDto<AdvertisementSnapshotDto>> result = service.mergeMediaChanges(List.of(item));

        assertThat(result.get(0).changes()).isSameAs(item.changes());
    }

    // ── enrichActivityItems() (Activity tab) ────────────────────────────────────────────────

    @Test
    void enrichActivityItems_attachmentSnapshotUnchanged_noMediaChangesAdded() {
        stubAvailable(attachmentAuditHookFactory, attachmentAuditHook);

        AuditActivityItemDto<AdvertisementSnapshotDto> item = new AuditActivityItemDto<>(
                1L, 2, ActionType.UPDATED, 10L, null, List.of(),
                null, snapshot(List.of(), 50L), snapshot(List.of(), 50L));

        List<AuditActivityItemDto<AdvertisementSnapshotDto>> result = service.enrichActivityItems(List.of(item));

        verify(attachmentAuditHook, never()).getChangesBySnapshotId(any());
        assertThat(result.get(0)).isSameAs(item);
    }

    @Test
    void enrichActivityItems_attachmentSnapshotChanged_mediaChangesMerged() {
        stubAvailable(attachmentAuditHookFactory, attachmentAuditHook);
        when(attachmentAuditHook.getChangesBySnapshotId(20L))
                .thenReturn(List.of(new ChangeEntry.MediaChange("a.jpg", "b.jpg")));

        AuditActivityItemDto<AdvertisementSnapshotDto> item = new AuditActivityItemDto<>(
                1L, 2, ActionType.UPDATED, 10L, null, List.of(),
                null, snapshot(List.of(), 20L), snapshot(List.of(), 10L));

        List<AuditActivityItemDto<AdvertisementSnapshotDto>> result = service.enrichActivityItems(List.of(item));

        assertThat(result.get(0).changes()).hasSize(1);
        assertThat(result.get(0).changes().get(0)).isInstanceOf(ChangeEntry.MediaChange.class);
    }

    // ── getMediaStateForSnapshot() ───────────────────────────────────────────────────────────

    @Test
    void getMediaStateForSnapshot_nullSnapshotId_returnsNullWithoutCallingHook() {
        String state = service.getMediaStateForSnapshot(new EntityRef(EntityType.ADVERTISEMENT, 1L), null);

        assertThat(state).isNull();
        verifyNoInteractions(attachmentAuditHookFactory);
    }

    @Test
    void getMediaStateForSnapshot_hookAbsent_returnsNull() {
        String state = service.getMediaStateForSnapshot(new EntityRef(EntityType.ADVERTISEMENT, 1L), 50L);

        assertThat(state).isNull();
    }

    @Test
    void getMediaStateForSnapshot_hookAvailable_returnsHookResult() {
        stubAvailable(attachmentAuditHookFactory, attachmentAuditHook);
        EntityRef ref = new EntityRef(EntityType.ADVERTISEMENT, 1L);
        when(attachmentAuditHook.getMediaStateForSnapshot(ref, 50L)).thenReturn("2 files");

        String state = service.getMediaStateForSnapshot(ref, 50L);

        assertThat(state).isEqualTo("2 files");
    }
}
