package org.ost.attachment.listener;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.AdvertisementDeletedEvent;
import org.ost.advertisement.events.AdvertisementRestoredEvent;
import org.ost.attachment.service.AttachmentService;
import org.ost.attachment.service.PhotoSnapshotService;
import org.ost.storage.api.ConditionalOnStorageEnabled;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@ConditionalOnStorageEnabled
public class AttachmentEventListener {

    private final AttachmentService    attachmentService;
    private final PhotoSnapshotService photoSnapshotService;

    @EventListener
    public void on(AdvertisementDeletedEvent event) {
        attachmentService.softDeleteAll(event.adId(), event.userId());
    }

    @EventListener
    public void on(AdvertisementRestoredEvent event) {
        String[] targetUrls = photoSnapshotService.getUrlsAtVersion(event.adId(), event.snapshotVersion());
        attachmentService.restoreToUrls(event.adId(), targetUrls, event.userId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    photoSnapshotService.capture(event.adId(), event.userId());
                }
            });
        } else {
            photoSnapshotService.capture(event.adId(), event.userId());
        }
    }
}
