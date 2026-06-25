package org.ost.attachment.services;

import lombok.RequiredArgsConstructor;
import org.ost.attachment.repository.AttachmentMediaChange;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.repository.AttachmentSnapshotRepository;
import org.ost.platform.attachment.util.YoutubeUtil;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentSnapshotService {

    private final AttachmentRepository         attachmentRepository;
    private final AttachmentSnapshotRepository attachmentSnapshotRepository;

    @Transactional
    public void capture(EntityType entityType, Long entityId, Long actorId) {
        List<String> currentUrls = attachmentRepository.getActiveUrls(entityType, entityId);
        Optional<List<String>> prevOpt = attachmentSnapshotRepository.getPrevUrls(entityType, entityId);
        if (prevOpt.isEmpty()) {
            if (currentUrls.isEmpty()) return;
            List<String> currNames = currentUrls.stream().map(AttachmentSnapshotService::filename).toList();
            attachmentSnapshotRepository.insert(entityType, entityId, currentUrls.toArray(new String[0]),
                    List.of(new AttachmentMediaChange(null, currNames)), actorId);
            return;
        }
        AttachmentMediaChange diff = buildDiff(prevOpt.get(), currentUrls);
        if (diff == null) return;
        attachmentSnapshotRepository.insert(entityType, entityId, currentUrls.toArray(new String[0]), List.of(diff), actorId);
    }

    public String getMediaStateAtVersion(EntityType entityType, Long entityId, int version) {
        String[] urls = attachmentSnapshotRepository.getUrlsAtVersion(entityType, entityId, version);
        if (urls.length == 0) return "";
        return Arrays.stream(urls).map(AttachmentSnapshotService::filename).collect(Collectors.joining(", "));
    }

    public String getMediaStateForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return attachmentSnapshotRepository.getUrlsForSnapshot(entityType, entityId, snapshotId)
                .map(l -> l.stream().map(AttachmentSnapshotService::filename).collect(Collectors.joining(", ")))
                .orElse("");
    }

    public String[] getUrlsAtVersion(EntityType entityType, Long entityId, int version) {
        return attachmentSnapshotRepository.getUrlsAtVersion(entityType, entityId, version);
    }

    public boolean mediaMatchCurrent(EntityType entityType, Long entityId, int version) {
        List<String> atVersion = List.of(attachmentSnapshotRepository.getUrlsAtVersion(entityType, entityId, version));
        List<String> current   = attachmentRepository.getActiveUrls(entityType, entityId);
        List<String> atNames   = atVersion.stream().map(AttachmentSnapshotService::filename).sorted().toList();
        List<String> curNames  = current.stream().map(AttachmentSnapshotService::filename).sorted().toList();
        return atNames.equals(curNames);
    }

    public List<ChangeEntry> getChangesForVersion(EntityType entityType, Long entityId, int version) {
        return attachmentSnapshotRepository.findChangesByVersion(entityType, entityId, version)
                .map(AttachmentSnapshotService::toChangeEntries)
                .orElse(List.of());
    }

    public List<AuditTimelineItemDto<AuditableSnapshot>> mergeAttachmentMediaChanges(List<AuditTimelineItemDto<AuditableSnapshot>> baseItems) {
        return baseItems.stream()
                .map(item -> {
                    List<ChangeEntry> mediaChanges = getChangesForSnapshot(item.entityRef().entityType(), item.entityRef().entityId(), item.snapshotId());
                    if (mediaChanges.isEmpty()) return item;
                    List<ChangeEntry> merged = new ArrayList<>(mediaChanges);
                    merged.addAll(item.changes());
                    return item.withChanges(merged);
                })
                .toList();
    }

    public List<ChangeEntry> getChangesForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return attachmentSnapshotRepository.findChangesBySnapshotId(entityType, entityId, snapshotId)
                .map(AttachmentSnapshotService::toChangeEntries)
                .orElse(List.of());
    }

    private static List<ChangeEntry> toChangeEntries(List<AttachmentMediaChange> changes) {
        return changes.stream()
                .map(c -> {
                    String before;
                    if (c.before() == null) {
                        before = null;
                    } else {
                        before = c.before().isEmpty() ? "—" : String.join(", ", c.before());
                    }
                    String after  = c.after().isEmpty() ? "—" : String.join(", ", c.after());
                    return (ChangeEntry) new ChangeEntry.MediaChange(before, after);
                })
                .toList();
    }

    private static AttachmentMediaChange buildDiff(List<String> prev, List<String> curr) {
        List<String> prevNames = prev.stream().map(AttachmentSnapshotService::filename).toList();
        List<String> currNames = curr.stream().map(AttachmentSnapshotService::filename).toList();
        return Objects.equals(prevNames, currNames) ? null : new AttachmentMediaChange(prevNames, currNames);
    }

    private static String filename(String url) {
        if (url == null || url.isBlank()) return "";
        String ytId = YoutubeUtil.extractId(url);
        if (ytId != null) return YoutubeUtil.filename(ytId);
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }
}
