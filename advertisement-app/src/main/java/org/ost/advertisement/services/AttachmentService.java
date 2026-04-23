package org.ost.advertisement.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.Attachment;
import org.ost.advertisement.entities.EntityType;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.repository.attachment.AttachmentRepository;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.security.UserIdMarker;
import org.ost.advertisement.services.auth.AuthContextService;
import org.ost.storage.api.ConditionalOnStorageEnabled;
import org.ost.storage.api.StorageService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.sql.Array;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    private final AttachmentRepository        repository;
    private final StorageService              storageService;
    private final AccessEvaluator             access;
    private final NamedParameterJdbcTemplate  jdbc;
    private final SnapshotService             snapshotService;
    private final AuthContextService          authContextService;

    public List<Attachment> getByEntityId(EntityType entityType, Long entityId) {
        return repository.findByEntityTypeAndEntityIdAndDeletedAtIsNull(entityType, entityId);
    }

    public Attachment upload(UserIdMarker owner, EntityType entityType, Long entityId,
                             String filename, InputStream inputStream,
                             long contentLength, String contentType) {
        if (access.canNotEdit(owner)) {
            throw new AccessDeniedException("You cannot add attachments to this entity");
        }

        // S3 upload happens before any DB connection is acquired
        String url = storageService.upload(
                entityType.name().toLowerCase() + "s/" + entityId,
                filename, inputStream, contentLength, contentType);

        try {
            Attachment saved = repository.save(Attachment.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .url(url)
                    .filename(filename)
                    .contentType(contentType)
                    .size(contentLength)
                    .build());

            if (entityType == EntityType.ADVERTISEMENT) {
                capturePhotoChanges(entityId);
            }
            return saved;
        } catch (Exception e) {
            storageService.delete(url);
            throw e;
        }
    }

    @Transactional
    public void delete(UserIdMarker owner, Long attachmentId) {
        if (access.canNotDelete(owner)) {
            throw new AccessDeniedException("You cannot delete this attachment");
        }
        repository.findById(attachmentId).ifPresent(attachment -> {
            softDeleteAttachment(attachmentId, owner.getOwnerUserId());
            if (attachment.getEntityType() == EntityType.ADVERTISEMENT) {
                capturePhotoChanges(attachment.getEntityId());
            }
        });
    }

    /** Soft-deletes an attachment without creating a snapshot entry (used during form edit). */
    @Transactional
    public void deleteSkipSnapshot(UserIdMarker owner, Long attachmentId) {
        if (access.canNotDelete(owner)) {
            throw new AccessDeniedException("You cannot delete this attachment");
        }
        softDeleteAttachment(attachmentId, owner.getOwnerUserId());
    }

    private void softDeleteAttachment(Long id, Long deletedBy) {
        jdbc.update(
            "UPDATE attachment SET deleted_at = NOW(), deleted_by_user_id = :deletedBy WHERE id = :id",
            new MapSqlParameterSource().addValue("id", id).addValue("deletedBy", deletedBy)
        );
    }

    /** Captures photo changes into the snapshot — diff computed from DB state. */
    public void capturePhotoChanges(Long advertisementId) {
        Long userId = authContextService.getCurrentUser().map(User::getId).orElse(null);
        if (userId != null) {
            snapshotService.captureAdvertisementAttachmentChange(advertisementId, userId);
        }
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
        commitTempUploadsQuiet(owner, entityType, entityId, temps);

        if (entityType == EntityType.ADVERTISEMENT) {
            capturePhotoChanges(entityId);
        }
    }

    /** Moves temp files to final location without creating a snapshot entry. */
    public void commitTempUploadsQuiet(UserIdMarker owner, EntityType entityType, Long entityId,
                                       List<TempAttachment> temps) {
        if (temps.isEmpty()) return;
        if (access.canNotEdit(owner)) {
            throw new AccessDeniedException("You cannot add attachments to this entity");
        }

        // 1. All S3 moves happen before any DB connection is acquired
        String folder = entityType.name().toLowerCase() + "s/" + entityId;
        List<Attachment> toSave = temps.stream()
                .map(temp -> Attachment.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .url(storageService.move(temp.tempUrl(), folder, temp.filename()))
                        .filename(temp.filename())
                        .contentType(temp.contentType())
                        .size(temp.size())
                        .build())
                .toList();

        // 2. Then persist all to DB; on failure roll back S3 moves
        try {
            repository.saveAll(toSave);
        } catch (Exception e) {
            toSave.forEach(a -> storageService.delete(a.getUrl()));
            throw e;
        }
    }

    /**
     * Restores attachment state to match the given URL set:
     * un-deletes attachments whose URL is in targetUrls,
     * soft-deletes active attachments whose URL is not in targetUrls.
     */
    @Transactional
    public void restoreToUrls(Long adId, String[] targetUrls, Long restoredByUserId) {
        Set<String> target = (targetUrls != null) ? Set.copyOf(Arrays.asList(targetUrls)) : Set.of();

        if (target.isEmpty()) {
            // soft-delete everything currently active
            jdbc.update(
                "UPDATE attachment SET deleted_at = NOW(), deleted_by_user_id = :userId " +
                "WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId AND deleted_at IS NULL",
                new MapSqlParameterSource().addValue("userId", restoredByUserId).addValue("adId", adId)
            );
            return;
        }

        Array urlArray = jdbc.getJdbcOperations().execute(
                (Connection conn) -> conn.createArrayOf("text", target.toArray(new String[0]))
        );

        // Un-delete attachments that belong to the snapshot
        jdbc.update(
            "UPDATE attachment SET deleted_at = NULL, deleted_by_user_id = NULL " +
            "WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId AND url = ANY(:urls)",
            new MapSqlParameterSource().addValue("adId", adId).addValue("urls", urlArray)
        );

        // Soft-delete attachments added after the snapshot
        jdbc.update(
            "UPDATE attachment SET deleted_at = NOW(), deleted_by_user_id = :userId " +
            "WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId " +
            "  AND deleted_at IS NULL AND NOT (url = ANY(:urls))",
            new MapSqlParameterSource().addValue("adId", adId).addValue("userId", restoredByUserId).addValue("urls", urlArray)
        );
    }

    @Transactional
    public void softDeleteAll(EntityType entityType, Long entityId, Long deletedByUserId) {
        jdbc.update(
            "UPDATE attachment SET deleted_at = NOW(), deleted_by_user_id = :deletedBy " +
            "WHERE entity_type = :type AND entity_id = :entityId AND deleted_at IS NULL",
            new MapSqlParameterSource()
                .addValue("deletedBy", deletedByUserId)
                .addValue("type",      entityType.name())
                .addValue("entityId",  entityId)
        );
    }

    public void discardTempUploads(List<TempAttachment> temps) {
        temps.forEach(t -> storageService.delete(t.tempUrl()));
    }
}
