package org.ost.attachment.listener;

import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.event.AdvertisementDeletedEvent;
import org.ost.platform.attachment.event.AdvertisementRestoredEvent;
import org.ost.attachment.service.AttachmentService;
import org.ost.attachment.service.AttachmentSnapshotService;
import org.ost.platform.attachment.storage.ConditionalOnStorageEnabled;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@RequiredArgsConstructor
@ConditionalOnStorageEnabled
public class AttachmentEventListener {

    private final AttachmentService         attachmentService;
    private final AttachmentSnapshotService attachmentSnapshotService;

    @EventListener
    public void on(AdvertisementDeletedEvent event) {
        attachmentService.softDeleteAll(event.adId(), event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AdvertisementRestoredEvent event) {
        String[] targetUrls = attachmentSnapshotService.getUrlsAtVersion(event.adId(), event.snapshotVersion());
        attachmentService.restoreToUrls(event.adId(), targetUrls, event.userId());
        attachmentSnapshotService.capture(event.adId(), event.userId());
    }
}
