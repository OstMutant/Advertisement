package org.ost.advertisement.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.AdvertisementAttachment;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.repository.advertisement.AdvertisementAttachmentRepository;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.security.UserIdMarker;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttachmentService {

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

    public void deleteAllByAdvertisementId(UserIdMarker target, Long advertisementId) {
        if (access.canNotDelete(target)) {
            throw new AccessDeniedException("You cannot delete attachments of this advertisement");
        }
        repository.findByAdvertisementId(advertisementId).forEach(attachment ->
                storageService.delete(attachment.getUrl()));
        repository.deleteByAdvertisementId(advertisementId);
    }
}