package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.model.AttachmentLimits;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;

/** Adds, lists and removes an advertisement's photos and video links for a signed-in caller with the same rules as the gallery in the edit form, and records every change on the advertisement's history. */
@Service
@RequiredArgsConstructor
public class AdvertisementMediaService {

    private final AttachmentMediaService attachmentMediaService;
    private final AdvertisementReadService advertisementReadService;
    private final AdvertisementSaveService advertisementSaveService;
    private final AuthorizationService authorizationService;
    private final TransactionTemplate tx;

    public List<AttachmentItemDto> list(@NonNull Long adId) {
        requireExists(adId);
        return attachmentMediaService.getByEntityId(EntityType.ADVERTISEMENT, adId);
    }

    /** One file of an upload batch. */
    public record UploadItem(String filename, InputStream content, long contentLength, String contentType) {
    }

    /** Stores the whole batch or none of its database rows: count and sizes are checked first, and one history entry is recorded for the batch. */
    public List<AttachmentItemDto> uploadAll(@NonNull Long actingUserId, @NonNull Long adId, @NonNull List<UploadItem> items) {
        requireCanEdit(actingUserId, adId);
        requireRoomFor(adId, items.size());
        items.forEach(item -> {
            if (item.contentLength() > AttachmentLimits.MAX_FILE_SIZE) {
                throw new IllegalArgumentException("A file may be at most %d MB".formatted(AttachmentLimits.MAX_FILE_SIZE / (1024 * 1024)));
            }
        });
        return tx.execute(status -> {
            List<AttachmentItemDto> saved = items.stream()
                    .map(item -> attachmentMediaService.upload(EntityType.ADVERTISEMENT, adId, item.filename(), item.content(),
                            item.contentLength(), item.contentType()))
                    .toList();
            advertisementSaveService.recordMediaChange(adId, actingUserId);
            return saved;
        });
    }

    public AttachmentItemDto addVideo(@NonNull Long actingUserId, @NonNull Long adId, @NonNull String url) {
        requireCanEdit(actingUserId, adId);
        requireRoomFor(adId, 1);
        return tx.execute(status -> {
            AttachmentItemDto saved = attachmentMediaService.addVideo(EntityType.ADVERTISEMENT, adId, url);
            advertisementSaveService.recordMediaChange(adId, actingUserId);
            return saved;
        });
    }

    public void delete(@NonNull Long actingUserId, @NonNull Long adId, @NonNull Long attachmentId) {
        requireCanEdit(actingUserId, adId);
        boolean belongs = attachmentMediaService.getByEntityId(EntityType.ADVERTISEMENT, adId).stream()
                .anyMatch(item -> item.id().equals(attachmentId));
        if (!belongs) {
            throw new NoSuchElementException("Attachment " + attachmentId + " not found on advertisement " + adId);
        }
        tx.executeWithoutResult(status -> {
            attachmentMediaService.delete(attachmentId);
            attachmentMediaService.captureSnapshot(EntityType.ADVERTISEMENT, adId);
            advertisementSaveService.recordMediaChange(adId, actingUserId);
        });
    }

    private AdvertisementInfoDto requireExists(Long adId) {
        return advertisementReadService.findById(adId)
                .orElseThrow(() -> new NoSuchElementException("Advertisement " + adId + " not found"));
    }

    private void requireCanEdit(Long actingUserId, Long adId) {
        authorizationService.requireCanOperate(actingUserId, requireExists(adId).getOwnerUserId());
    }

    private void requireRoomFor(Long adId, int newItems) {
        if (attachmentMediaService.getByEntityId(EntityType.ADVERTISEMENT, adId).size() + newItems > AttachmentLimits.MAX_FILES) {
            throw new IllegalArgumentException("A gallery holds at most %d items".formatted(AttachmentLimits.MAX_FILES));
        }
    }
}
