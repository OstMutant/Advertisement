package org.ost.attachment.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.repository.AttachmentMediaChange;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.repository.AttachmentSnapshotRepository;
import org.ost.platform.attachment.util.YoutubeUtil;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentSnapshotService {

    private final AttachmentRepository         attachmentRepository;
    private final AttachmentSnapshotRepository attachmentSnapshotRepository;

    @Transactional
    public void capture(EntityType entityType, Long entityId, Long actorId) {
        captureAndGetId(entityType, entityId, actorId);
    }

    @Transactional
    public Optional<Long> captureAndGetId(EntityType entityType, Long entityId, Long actorId) {
        log.info("Attachment snapshot capture: entityType={}, entityId={}", entityType, entityId);
        List<String> currentUrls = attachmentRepository.getActiveUrls(entityType, entityId);
        Optional<List<String>> prevOpt = attachmentSnapshotRepository.getPrevUrls(entityType, entityId);
        if (prevOpt.isEmpty()) {
            if (currentUrls.isEmpty()) return Optional.empty();
            Map<String, String> urlToFilename = resolveFilenames(entityType, entityId, currentUrls);
            List<String> currNames = currentUrls.stream().map(u -> filename(u, urlToFilename)).toList();
            attachmentSnapshotRepository.insert(entityType, entityId, currentUrls.toArray(new String[0]),
                    List.of(new AttachmentMediaChange(null, currNames)), actorId);
            return attachmentSnapshotRepository.findLatestId(entityType, entityId);
        }
        AttachmentMediaChange diff = buildDiff(entityType, entityId, prevOpt.get(), currentUrls);
        if (diff == null) return Optional.empty();
        attachmentSnapshotRepository.insert(entityType, entityId, currentUrls.toArray(new String[0]), List.of(diff), actorId);
        return attachmentSnapshotRepository.findLatestId(entityType, entityId);
    }

    public String[] getUrlsBySnapshotId(Long snapshotId) {
        return attachmentSnapshotRepository.getUrlsById(snapshotId)
                .map(l -> l.toArray(new String[0]))
                .orElse(new String[0]);
    }

    public Long getLatestSnapshotId(EntityType entityType, Long entityId) {
        return attachmentSnapshotRepository.findLatestId(entityType, entityId).orElse(null);
    }

    public String getMediaStateForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return attachmentSnapshotRepository.getUrlsById(snapshotId)
                .map(urls -> {
                    Map<String, String> urlToFilename = resolveFilenames(entityType, entityId, urls);
                    return urls.stream().map(u -> filename(u, urlToFilename)).collect(Collectors.joining(", "));
                })
                .orElse("");
    }

    public List<ChangeEntry> getChangesBySnapshotId(Long attachmentSnapshotId) {
        return attachmentSnapshotRepository.findChangesById(attachmentSnapshotId)
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

    private AttachmentMediaChange buildDiff(EntityType entityType, Long entityId, List<String> prev, List<String> curr) {
        Map<String, String> urlToFilename = resolveFilenames(entityType, entityId,
                Stream.concat(prev.stream(), curr.stream()).distinct().toList());
        List<String> prevNames = prev.stream().map(u -> filename(u, urlToFilename)).toList();
        List<String> currNames = curr.stream().map(u -> filename(u, urlToFilename)).toList();
        return Objects.equals(prevNames, currNames) ? null : new AttachmentMediaChange(prevNames, currNames);
    }

    // Keyed by url, not filename -- duplicate original filenames across attachments can't collide.
    private Map<String, String> resolveFilenames(EntityType entityType, Long entityId, List<String> urls) {
        if (urls.isEmpty()) return Map.of();
        return attachmentRepository.findByEntityAndUrls(entityType, entityId, urls.toArray(new String[0])).stream()
                .collect(Collectors.toMap(Attachment::getUrl, Attachment::getFilename));
    }

    private static String filename(String url, Map<String, String> urlToFilename) {
        if (url == null || url.isBlank()) return "";
        String ytId = YoutubeUtil.extractId(url);
        if (ytId != null) return YoutubeUtil.filename(ytId);
        String resolved = urlToFilename.get(url);
        if (resolved != null) return resolved;
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }
}
