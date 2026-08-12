package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.spi.CurrentLocaleHook;
import org.ost.orchestrator.spi.UiLabelHook;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.attachment.spi.AttachmentAuditPort;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * {@link AdvertisementAuditEnrichService} merges attachment-port media changes into audit
 * timeline/activity entries and resolves raw category ids into names. Both optional ports
 * ({@link TaxonPort}, {@link AttachmentAuditPort}) must degrade gracefully when absent
 * (improvement-048).
 */
@ExtendWith(MockitoExtension.class)
class AdvertisementAuditEnrichServiceTest {

    @Mock private AttachmentMediaService attachmentMediaService;
    @Mock private TaxonLookupService taxonLookupService;
    @Mock private CurrentLocaleHook currentLocaleHook;
    @Mock private UiLabelHook uiLabelHook;

    private AdvertisementAuditEnrichService service;

    @BeforeEach
    void setUp() {
        lenient().when(currentLocaleHook.getCurrentLocale()).thenReturn(Locale.ENGLISH);
        lenient().when(uiLabelHook.labelFor(AdKind.OFFER)).thenReturn("Offer");
        lenient().when(uiLabelHook.labelFor(AdKind.PRODUCT)).thenReturn("Product");
        lenient().when(uiLabelHook.noMediaPlaceholder()).thenReturn("—");
        service = new AdvertisementAuditEnrichService(attachmentMediaService, taxonLookupService, currentLocaleHook, uiLabelHook);
    }

    private static TaxonDto taxon(Long id, String name) {
        return TaxonDto.builder().id(id).type(TaxonType.CATEGORY).name(name).description("").build();
    }

    private static TaxonDto city(Long id, String name) {
        return TaxonDto.builder().id(id).type(TaxonType.CITY).name(name).description("").build();
    }

    private static AdvertisementSnapshotDto snapshot(List<Long> categoryIds, Long attachmentSnapshotId) {
        return new AdvertisementSnapshotDto("Title", "Desc", AdKind.OFFER, categoryIds, null, attachmentSnapshotId);
    }

    private static AdvertisementSnapshotDto snapshot(List<Long> categoryIds, Long cityTaxonId, Long attachmentSnapshotId) {
        return new AdvertisementSnapshotDto("Title", "Desc", AdKind.OFFER, categoryIds, cityTaxonId, attachmentSnapshotId);
    }

    // ── mergeMediaChanges() (Timeline tab) ──────────────────────────────────────────────────

    @Test
    void mergeMediaChanges_mediaHookAvailable_mediaChangesPrecedeResolvedCategoryChanges() {
        when(taxonLookupService.findByIds(Set.of(1L), Locale.ENGLISH)).thenReturn(Map.of(1L, taxon(1L, "Electronics")));
        when(attachmentMediaService.getChangesBySnapshotId(50L))
                .thenReturn(List.of(new ChangeEntry.MediaChange("old.jpg", "new.jpg")));

        AuditTimelineItemDto<AdvertisementSnapshotDto> item = new AuditTimelineItemDto<>(
                1L, new EntityRef(EntityType.ADVERTISEMENT, 1L), ActionType.UPDATED, null,
                List.of(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.categoryIds, "", "1")),
                10L, snapshot(List.of(1L), 50L), null);

        List<AuditTimelineItemDto<AdvertisementSnapshotDto>> result = service.mergeMediaChanges(List.of(item));

        List<ChangeEntry> changes = result.getFirst().changes();
        assertThat(changes).hasSize(2);
        assertThat(changes.getFirst()).isInstanceOf(ChangeEntry.MediaChange.class);
        assertThat(((ChangeEntry.FieldChange) changes.get(1)).to()).isEqualTo("Electronics");
    }

    @Test
    void mergeMediaChanges_nonAdvertisementEntity_passesThroughUnchanged() {
        AuditTimelineItemDto<AdvertisementSnapshotDto> item = new AuditTimelineItemDto<>(
                1L, new EntityRef(EntityType.USER, 1L), ActionType.UPDATED, null,
                List.of(), 10L, null, null);

        List<AuditTimelineItemDto<AdvertisementSnapshotDto>> result = service.mergeMediaChanges(List.of(item));

        assertThat(result.getFirst()).isSameAs(item);
    }

    @Test
    void mergeMediaChanges_hookAbsent_noMediaChangesAddedButCategoriesStillResolved() {
        when(taxonLookupService.findByIds(Set.of(1L), Locale.ENGLISH)).thenReturn(Map.of(1L, taxon(1L, "Electronics")));

        AuditTimelineItemDto<AdvertisementSnapshotDto> item = new AuditTimelineItemDto<>(
                1L, new EntityRef(EntityType.ADVERTISEMENT, 1L), ActionType.UPDATED, null,
                List.of(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.categoryIds, "", "1")),
                10L, snapshot(List.of(1L), 50L), null);

        List<AuditTimelineItemDto<AdvertisementSnapshotDto>> result = service.mergeMediaChanges(List.of(item));

        List<ChangeEntry> changes = result.getFirst().changes();
        assertThat(changes).hasSize(1);
        assertThat(((ChangeEntry.FieldChange) changes.getFirst()).to()).isEqualTo("Electronics");
    }

    @Test
    void mergeMediaChanges_categoryUnchanged_stillAddsResolvedCategoryEntry() {
        when(taxonLookupService.findByIds(Set.of(1L), Locale.ENGLISH)).thenReturn(Map.of(1L, taxon(1L, "Electronics")));
        when(attachmentMediaService.getChangesBySnapshotId(50L))
                .thenReturn(List.of(new ChangeEntry.MediaChange("old.jpg", "new.jpg")));

        AuditTimelineItemDto<AdvertisementSnapshotDto> item = new AuditTimelineItemDto<>(
                1L, new EntityRef(EntityType.ADVERTISEMENT, 1L), ActionType.UPDATED, null,
                List.of(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.title, "Old", "New")),
                10L, snapshot(List.of(1L), 50L), snapshot(List.of(1L), 40L));

        List<AuditTimelineItemDto<AdvertisementSnapshotDto>> result = service.mergeMediaChanges(List.of(item));

        ChangeEntry.FieldChange categoryEntry = result.getFirst().changes().stream()
                .filter(ChangeEntry.FieldChange.class::isInstance)
                .map(ChangeEntry.FieldChange.class::cast)
                .filter(fc -> fc.field().equals(AdvertisementSnapshotDto.Fields.categoryIds))
                .findFirst().orElseThrow();
        assertThat(categoryEntry.to()).isEqualTo("Electronics");
    }

    @Test
    void mergeMediaChanges_cityUnchanged_stillAddsResolvedCityEntry() {
        when(taxonLookupService.findByIds(Set.of(5L), Locale.ENGLISH)).thenReturn(Map.of(5L, city(5L, "Lviv")));
        when(attachmentMediaService.getChangesBySnapshotId(50L))
                .thenReturn(List.of(new ChangeEntry.MediaChange("old.jpg", "new.jpg")));

        AuditTimelineItemDto<AdvertisementSnapshotDto> item = new AuditTimelineItemDto<>(
                1L, new EntityRef(EntityType.ADVERTISEMENT, 1L), ActionType.UPDATED, null,
                List.of(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.title, "Old", "New")),
                10L, snapshot(List.of(), 5L, 50L), snapshot(List.of(), 5L, 40L));

        List<AuditTimelineItemDto<AdvertisementSnapshotDto>> result = service.mergeMediaChanges(List.of(item));

        ChangeEntry.FieldChange cityEntry = result.getFirst().changes().stream()
                .filter(ChangeEntry.FieldChange.class::isInstance)
                .map(ChangeEntry.FieldChange.class::cast)
                .filter(fc -> fc.field().equals(AdvertisementSnapshotDto.Fields.cityTaxonId))
                .findFirst().orElseThrow();
        assertThat(cityEntry.to()).isEqualTo("Lviv");
    }

    @Test
    void enrichActivityItems_adKindChanged_resolvesToLocalizedLabel() {
        AdvertisementSnapshotDto curr = new AdvertisementSnapshotDto("Title", "Desc", AdKind.PRODUCT, List.of(), null, 50L);
        AdvertisementSnapshotDto prev = new AdvertisementSnapshotDto("Title", "Desc", AdKind.OFFER, List.of(), null, 50L);
        AuditActivityItemDto<AdvertisementSnapshotDto> item = new AuditActivityItemDto<>(
                1L, 2, ActionType.UPDATED, 10L, null,
                List.of(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.adKind, "OFFER", "PRODUCT")),
                null, curr, prev);

        List<AuditActivityItemDto<AdvertisementSnapshotDto>> result = service.enrichActivityItems(List.of(item));

        ChangeEntry.FieldChange typeEntry = (ChangeEntry.FieldChange) result.getFirst().changes().getFirst();
        assertThat(typeEntry.from()).isEqualTo("Offer");
        assertThat(typeEntry.to()).isEqualTo("Product");
    }

    @Test
    void enrichActivityItems_adKindUnchanged_noEntryManufactured() {
        AuditActivityItemDto<AdvertisementSnapshotDto> item = new AuditActivityItemDto<>(
                1L, 2, ActionType.UPDATED, 10L, null,
                List.of(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.title, "Old", "New")),
                null, snapshot(List.of(), 50L), snapshot(List.of(), 50L));

        List<AuditActivityItemDto<AdvertisementSnapshotDto>> result = service.enrichActivityItems(List.of(item));

        assertThat(result.getFirst().changes()).hasSize(1);
        assertThat(result.getFirst().changes().getFirst()).isInstanceOf(ChangeEntry.FieldChange.class);
    }

    @Test
    void mergeMediaChanges_taxonPortAbsent_fallsBackToRawIdString() {
        AuditTimelineItemDto<AdvertisementSnapshotDto> item = new AuditTimelineItemDto<>(
                1L, new EntityRef(EntityType.ADVERTISEMENT, 1L), ActionType.UPDATED, null,
                List.of(new ChangeEntry.FieldChange(AdvertisementSnapshotDto.Fields.categoryIds, "", "1")),
                10L, snapshot(List.of(1L), null), null);

        List<AuditTimelineItemDto<AdvertisementSnapshotDto>> result = service.mergeMediaChanges(List.of(item));

        List<ChangeEntry> changes = result.getFirst().changes();
        assertThat(changes).hasSize(2);
        assertThat(changes.getFirst()).isInstanceOf(ChangeEntry.MediaChange.class);
        assertThat(((ChangeEntry.FieldChange) changes.get(1)).to()).isEqualTo("1");
    }

    @Test
    void mergeMediaChanges_noAttachmentSnapshotEver_addsNoMediaEntry() {
        AuditTimelineItemDto<AdvertisementSnapshotDto> item = new AuditTimelineItemDto<>(
                1L, new EntityRef(EntityType.ADVERTISEMENT, 1L), ActionType.CREATED, null,
                List.of(), 10L, snapshot(List.of(), null), null);

        List<AuditTimelineItemDto<AdvertisementSnapshotDto>> result = service.mergeMediaChanges(List.of(item));

        List<ChangeEntry> changes = result.getFirst().changes();
        assertThat(changes).hasSize(1);
        ChangeEntry.MediaChange mediaChange = (ChangeEntry.MediaChange) changes.getFirst();
        assertThat(mediaChange.before()).isNull();
        assertThat(mediaChange.after()).isEqualTo("—");
    }

    @Test
    void enrichActivityItems_noAttachmentSnapshotEver_addsNoMediaEntry() {
        AuditActivityItemDto<AdvertisementSnapshotDto> item = new AuditActivityItemDto<>(
                1L, 1, ActionType.CREATED, 10L, null, List.of(),
                null, snapshot(List.of(), null), null);

        List<AuditActivityItemDto<AdvertisementSnapshotDto>> result = service.enrichActivityItems(List.of(item));

        List<ChangeEntry> changes = result.getFirst().changes();
        assertThat(changes).hasSize(1);
        ChangeEntry.MediaChange mediaChange = (ChangeEntry.MediaChange) changes.getFirst();
        assertThat(mediaChange.before()).isNull();
        assertThat(mediaChange.after()).isEqualTo("—");
    }

    // ── enrichActivityItems() (Activity tab) ────────────────────────────────────────────────

    @Test
    void enrichActivityItems_attachmentSnapshotUnchanged_noMediaChangesAdded() {
        AuditActivityItemDto<AdvertisementSnapshotDto> item = new AuditActivityItemDto<>(
                1L, 2, ActionType.UPDATED, 10L, null, List.of(),
                null, snapshot(List.of(), 50L), snapshot(List.of(), 50L));

        List<AuditActivityItemDto<AdvertisementSnapshotDto>> result = service.enrichActivityItems(List.of(item));

        verify(attachmentMediaService, never()).getChangesBySnapshotId(any());
        assertThat(result.getFirst()).isSameAs(item);
    }

    @Test
    void enrichActivityItems_attachmentSnapshotChanged_mediaChangesMerged() {
        when(attachmentMediaService.getChangesBySnapshotId(20L))
                .thenReturn(List.of(new ChangeEntry.MediaChange("a.jpg", "b.jpg")));

        AuditActivityItemDto<AdvertisementSnapshotDto> item = new AuditActivityItemDto<>(
                1L, 2, ActionType.UPDATED, 10L, null, List.of(),
                null, snapshot(List.of(), 20L), snapshot(List.of(), 10L));

        List<AuditActivityItemDto<AdvertisementSnapshotDto>> result = service.enrichActivityItems(List.of(item));

        assertThat(result.getFirst().changes()).hasSize(1);
        assertThat(result.getFirst().changes().getFirst()).isInstanceOf(ChangeEntry.MediaChange.class);
    }

    // ── getMediaStateForSnapshot() ───────────────────────────────────────────────────────────

    @Test
    void getMediaStateForSnapshot_nullSnapshotId_returnsNullWithoutCallingHook() {
        String state = service.getMediaStateForSnapshot(new EntityRef(EntityType.ADVERTISEMENT, 1L), null);

        assertThat(state).isNull();
        verifyNoInteractions(attachmentMediaService);
    }

    @Test
    void getMediaStateForSnapshot_hookAbsent_returnsNull() {
        String state = service.getMediaStateForSnapshot(new EntityRef(EntityType.ADVERTISEMENT, 1L), 50L);

        assertThat(state).isNull();
    }

    @Test
    void getMediaStateForSnapshot_hookAvailable_returnsHookResult() {
        EntityRef ref = new EntityRef(EntityType.ADVERTISEMENT, 1L);
        when(attachmentMediaService.getMediaStateForSnapshot(ref, 50L)).thenReturn("2 files");

        String state = service.getMediaStateForSnapshot(ref, 50L);

        assertThat(state).isEqualTo("2 files");
    }
}
