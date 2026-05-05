package org.ost.attachment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.repository.PhotoSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhotoSnapshotService {

    record PhotoChange(List<String> before, List<String> after) {}

    private final AttachmentRepository    attachmentRepository;
    private final PhotoSnapshotRepository photoSnapshotRepository;
    private final ObjectMapper            objectMapper;

    @Transactional
    public void capture(Long adId, Long userId) {
        List<String> currentUrls = attachmentRepository.getActiveUrls(adId);
        List<String> prevUrls    = photoSnapshotRepository.getPrevUrls(adId);
        PhotoChange  diff        = buildDiff(prevUrls, currentUrls);
        if (diff == null) return;

        photoSnapshotRepository.insert(adId, currentUrls.toArray(new String[0]), toJson(diff), userId);
    }

    public String getPhotoStateAtVersion(Long adId, int version) {
        String[] urls = photoSnapshotRepository.getUrlsAtVersion(adId, version);
        if (urls.length == 0) return "";
        return Arrays.stream(urls).map(PhotoSnapshotService::filename).collect(Collectors.joining(", "));
    }

    public String getPhotoStateForAdvSnapshot(Long adId, Long advSnapshotId) {
        return photoSnapshotRepository.getUrlsForAdvSnapshot(adId, advSnapshotId)
                .map(l -> l.stream().map(PhotoSnapshotService::filename).collect(Collectors.joining(", ")))
                .orElse("");
    }

    public String[] getUrlsAtVersion(Long adId, int version) {
        return photoSnapshotRepository.getUrlsAtVersion(adId, version);
    }

    public boolean photosMatchCurrent(Long adId, int version) {
        List<String> atVersion = List.of(photoSnapshotRepository.getUrlsAtVersion(adId, version));
        List<String> current   = attachmentRepository.getActiveUrls(adId);
        List<String> atNames   = atVersion.stream().map(PhotoSnapshotService::filename).sorted().toList();
        List<String> curNames  = current.stream().map(PhotoSnapshotService::filename).sorted().toList();
        return atNames.equals(curNames);
    }

    public List<ChangeEntry> getChangesForVersion(Long adId, int version) {
        return photoSnapshotRepository.getChangesJson(adId, version)
                .map(this::parsePhotoChanges)
                .orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public List<ChangeEntry> parsePhotoChanges(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw.stream()
                    .map(m -> {
                        List<String> before = (List<String>) m.getOrDefault("before", List.of());
                        List<String> after  = (List<String>) m.getOrDefault("after",  List.of());
                        String beforeStr = before.isEmpty() ? "—" : String.join(", ", before);
                        String afterStr  = after.isEmpty()  ? "—" : String.join(", ", after);
                        return (ChangeEntry) new ChangeEntry.GenericChange("changes.photos", beforeStr, afterStr);
                    })
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static PhotoChange buildDiff(List<String> prev, List<String> curr) {
        List<String> prevNames = prev.stream().map(PhotoSnapshotService::filename).toList();
        List<String> currNames = curr.stream().map(PhotoSnapshotService::filename).toList();
        return Objects.equals(prevNames, currNames) ? null : new PhotoChange(prevNames, currNames);
    }

    private String toJson(PhotoChange diff) {
        if (diff == null) return null;
        try {
            return objectMapper.writeValueAsString(List.of(diff));
        } catch (Exception e) {
            return null;
        }
    }

    private static String filename(String url) {
        if (url == null || url.isBlank()) return "";
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }
}
