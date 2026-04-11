package org.ost.advertisement.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.Attachment;
import org.ost.advertisement.entities.EntityType;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.repository.attachment.AttachmentRepository;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.security.UserIdMarker;
import org.ost.storage.api.ConditionalOnStorageEnabled;
import org.ost.storage.api.StorageService;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnStorageEnabled
public class AttachmentService {

    public record TempAttachment(
            String tempUrl,
            String filename,
            String contentType,
            long size
    ) {}

    private final AttachmentRepository repository;
    private final StorageService       storageService;
    private final AccessEvaluator      access;

    public List<Attachment> getByEntityId(EntityType entityType, Long entityId) {
        return repository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    public Attachment upload(UserIdMarker owner, EntityType entityType, Long entityId,
                             String filename, InputStream inputStream,
                             long contentLength, String contentType) {
        if (access.canNotEdit(owner)) {
            throw new AccessDeniedException("You cannot add attachments to this entity");
        }

        String url = storageService.upload(
                entityType.name().toLowerCase() + "s/" + entityId,
                filename, inputStream, contentLength, contentType);

        return repository.save(Attachment.builder()
                .entityType(entityType)
                .entityId(entityId)
                .url(url)
                .filename(filename)
                .contentType(contentType)
                .size(contentLength)
                .build());
    }

    public void delete(UserIdMarker owner, Long attachmentId) {
        if (access.canNotDelete(owner)) {
            throw new AccessDeniedException("You cannot delete this attachment");
        }
        repository.findById(attachmentId).ifPresent(attachment -> {
            storageService.delete(attachment.getUrl());
            repository.deleteById(attachmentId);
        });
    }

    public TempAttachment uploadTemp(String tempSessionId,
                                     String filename, InputStream inputStream,
                                     long contentLength, String contentType) {
        String tempUrl = storageService.upload(
                "temp/" + tempSessionId,
                filename, inputStream, contentLength, contentType);

        return new TempAttachment(tempUrl, filename, contentType, contentLength);
    }

    public void commitTempUploads(UserIdMarker owner, EntityType entityType, Long entityId,
                                  List<TempAttachment> temps) {
        if (temps.isEmpty()) return;
        if (access.canNotEdit(owner)) {
            throw new AccessDeniedException("You cannot add attachments to this entity");
        }

        for (TempAttachment temp : temps) {
            String finalUrl = storageService.move(
                    temp.tempUrl(),
                    entityType.name().toLowerCase() + "s/" + entityId,
                    temp.filename());

            repository.save(Attachment.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .url(finalUrl)
                    .filename(temp.filename())
                    .contentType(temp.contentType())
                    .size(temp.size())
                    .build());
        }
    }

    public void discardTempUploads(List<TempAttachment> temps) {
        temps.forEach(t -> storageService.delete(t.tempUrl()));
    }
}
