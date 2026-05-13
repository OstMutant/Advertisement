package org.ost.advertisement.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.model.AuditSnapshotMapper;
import org.ost.advertisement.audit.repository.AuditReadRepository;
import org.ost.advertisement.events.dto.AdvertisementHistoryDto;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class AuditHistoryService {

    private final AuditReadRepository                            auditReadRepository;
    private final AuditSnapshotMapper                           mapper;
    private final ObjectProvider<AdvertisementHistoryExtension> historyExtension;

    public List<AdvertisementHistoryDto> getAdvertisementHistory(Long advertisementId, Long currentUserId, boolean showAll) {
        List<AdvertisementHistoryDto> history = auditReadRepository.getAdvertisementHistory(
                advertisementId, showAll ? null : currentUserId);
        AdvertisementHistoryExtension ext = historyExtension.getIfAvailable();
        if (ext == null) return history;
        return history.stream()
                .map(h -> {
                    List<ChangeEntry> photoChanges = ext.getMediaChanges(advertisementId, h.version());
                    if (photoChanges.isEmpty()) return h;
                    List<ChangeEntry> combined = new ArrayList<>(photoChanges);
                    combined.addAll(h.changes());
                    return new AdvertisementHistoryDto(h.snapshotId(), h.version(), h.actionType(),
                            h.changedByUserName(), h.createdAt(), h.title(), h.description(),
                            combined, h.prevSnapshotId(), h.prevTitle(), h.prevDescription());
                })
                .toList();
    }

    @Transactional
    public void appendNoteToLastSnapshot(Long advertisementId, String note) {
        Long snapshotId = auditReadRepository.findLastSnapshotId(advertisementId).orElse(null);
        if (snapshotId == null) return;
        String currentJson = auditReadRepository.getChangesSummary(snapshotId);
        List<ChangeEntry> entries = new ArrayList<>(mapper.fromJsonList(currentJson));
        entries.add(new ChangeEntry.NoteEntry(note));
        auditReadRepository.updateChangesSummary(snapshotId, mapper.toJson(entries));
    }
}
