package org.ost.attachment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import org.ost.attachment.util.YoutubeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.repository.AttachmentSnapshotRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentSnapshotService {

    record MediaChange(List<String> before, List<String> after) {}

    private final AttachmentRepository         attachmentRepository;
    private final AttachmentSnapshotRepository attachmentSnapshotRepository;
    @Qualifier("attachmentObjectMapper")
    private final ObjectMapper                 objectMapper;

    @Transactional
    public void capture(EntityType entityType, Long entityId, Long actorId) {
        List<String> currentUrls = attachmentRepository.getActiveUrls(entityType, entityId);
        List<String> prevUrls    = attachmentSnapshotRepository.getPrevUrls(entityType, entityId);
        MediaChange  diff        = buildDiff(prevUrls, currentUrls);
        if (diff == null) return;

        attachmentSnapshotRepository.insert(entityType, entityId, currentUrls.toArray(new String[0]), toJson(diff), actorId);
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
        return attachmentSnapshotRepository.getChangesJson(entityType, entityId, version)
                .map(this::parseMediaChanges)
                .orElse(List.of());
    }

    public List<AuditActivityItemDto> mergeMediaChanges(List<AuditActivityItemDto> baseItems) {
        return baseItems.stream()
                .map(item -> {
                    List<ChangeEntry> mediaChanges = getChangesForSnapshot(item.entityType(), item.entityId(), item.snapshotId());
                    if (mediaChanges.isEmpty()) return item;
                    List<ChangeEntry> merged = new ArrayList<>(mediaChanges);
                    merged.addAll(item.changes());
                    return item.withChanges(merged);
                })
                .toList();
    }

    public List<ChangeEntry> getChangesForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return attachmentSnapshotRepository.getChangesJsonForSnapshot(entityType, entityId, snapshotId)
                .map(this::parseMediaChanges)
                .orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public List<ChangeEntry> parseMediaChanges(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw.stream()
                    .map(m -> {
                        List<String> before = (List<String>) m.getOrDefault("before", List.of());
                        List<String> after  = (List<String>) m.getOrDefault("after",  List.of());
                        String beforeStr = before.isEmpty() ? "—" : String.join(", ", before);
                        String afterStr  = after.isEmpty()  ? "—" : String.join(", ", after);
                        return (ChangeEntry) new ChangeEntry.GenericChange("audit.changes.media", beforeStr, afterStr);
                    })
                    .toList();
        } catch (Exception _) {
            return List.of();
        }
    }

    private static MediaChange buildDiff(List<String> prev, List<String> curr) {
        List<String> prevNames = prev.stream().map(AttachmentSnapshotService::filename).toList();
        List<String> currNames = curr.stream().map(AttachmentSnapshotService::filename).toList();
        return Objects.equals(prevNames, currNames) ? null : new MediaChange(prevNames, currNames);
    }

    private String toJson(MediaChange diff) {
        if (diff == null) return null;
        try {
            return objectMapper.writeValueAsString(List.of(diff));
        } catch (Exception _) {
            return null;
        }
    }

    private static String filename(String url) {
        if (url == null || url.isBlank()) return "";
        String ytId = YoutubeUtil.extractId(url);
        if (ytId != null) return "YouTube-" + ytId;
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }
}
