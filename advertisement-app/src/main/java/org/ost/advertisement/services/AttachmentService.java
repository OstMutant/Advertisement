package org.ost.advertisement.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.AdvertisementAttachment;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.repository.advertisement.AdvertisementAttachmentRepository;
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

    private final AdvertisementAttachmentRepository repository;
    private final StorageService storageService;
    private final AccessEvaluator access;

    public List<AdvertisementAttachment> getByAdvertisementId(Long advertisementId) {
        return repository.findByAdvertisementId(advertisementId);
    }

    public AdvertisementAttachment upload(UserIdMarker target, Long advertisementId,
                                          String filename, InputStream inputStream,
                                          long contentLength, String contentType) {
        if (access.canNotEdit(target)) {
            throw new AccessDeniedException("You cannot add attachments to this advertisement");
        }

        String url = storageService.upload("advertisements/" + advertisementId,
                filename, inputStream, contentLength, contentType);

        return repository.save(AdvertisementAttachment.builder()
                .advertisementId(advertisementId)
                .url(url)
                .filename(filename)
                .contentType(contentType)
                .size(contentLength)
                .build());
    }

    public void delete(UserIdMarker target, Long attachmentId) {
        if (access.canNotDelete(target)) {
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
                "advertisements/temp/" + tempSessionId,
                filename, inputStream, contentLength, contentType);

        return new TempAttachment(tempUrl, filename, contentType, contentLength);
    }

    public void commitTempUploads(UserIdMarker target, Long advertisementId,
                                  List<TempAttachment> temps) {
        if (temps.isEmpty()) return;
        if (access.canNotEdit(target)) {
            throw new AccessDeniedException("You cannot add attachments to this advertisement");
        }

        for (TempAttachment temp : temps) {
            String finalUrl = storageService.move(
                    temp.tempUrl(),
                    "advertisements/" + advertisementId,
                    temp.filename());

            repository.save(AdvertisementAttachment.builder()
                    .advertisementId(advertisementId)
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