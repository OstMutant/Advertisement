package org.ost.advertisement.services.audit;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.dto.AdvertisementHistoryDto;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.ost.advertisement.repository.audit.AuditLogRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditHistoryService {

    private final AuditLogRepository                            auditLogRepository;
    private final AuditSnapshotMapper                           mapper;
    private final ObjectProvider<AdvertisementHistoryExtension> historyExtension;

    public List<AdvertisementHistoryDto> getAdvertisementHistory(Long advertisementId, Long currentUserId, boolean showAll) {
        List<AdvertisementHistoryDto> history = auditLogRepository.getAdvertisementHistory(
                advertisementId, showAll ? null : currentUserId);
        AdvertisementHistoryExtension ext = historyExtension.getIfAvailable();
        if (ext == null) return history;
        return history.stream()
                .map(h -> {
                    List<ChangeEntry> photoChanges = ext.getPhotoChanges(advertisementId, h.version());
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
        Long snapshotId = auditLogRepository.findLastSnapshotId(advertisementId).orElse(null);
        if (snapshotId == null) return;
        String currentJson = auditLogRepository.getChangesSummary(snapshotId);
        List<ChangeEntry> entries = new ArrayList<>(mapper.fromJsonList(currentJson));
        entries.add(new ChangeEntry.NoteEntry(note));
        auditLogRepository.updateChangesSummary(snapshotId, mapper.toJson(entries));
    }
}
