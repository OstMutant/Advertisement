package org.ost.attachment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entity.Attachment;
import org.ost.attachment.repository.AttachmentDescriptor;
import org.ost.advertisement.events.spi.AttachmentCurrentUserProvider;
import org.ost.advertisement.events.AdvertisementMediaUpdatedEvent;
import org.ost.advertisement.spi.storage.ConditionalOnStorageEnabled;
import org.ost.advertisement.spi.storage.StorageService;
import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnStorageEnabled
public class AttachmentService {

    public record TempAttachment(String tempUrl, String filename, String contentType, long size) {}

    private static final AttachmentDescriptor ATTACHMENT_PROJECTION = new AttachmentDescriptor();

    private static final SqlWriteCommand INSERT_ATTACHMENT = SqlWriteCommand.of(
            "INSERT INTO " + AttachmentDescriptor.Write.TABLE +
            " (entity_type, entity_id, url, filename, content_type, size, created_at)" +
            " VALUES ('ADVERTISEMENT', :entityId, :url, :filename, :contentType, :size, NOW())" +
            " RETURNING " + AttachmentDescriptor.ID.columnName()
    );

    private static final SqlWriteCommand SOFT_DELETE = SqlWriteCommand.of(
            "UPDATE " + AttachmentDescriptor.Write.TABLE +
            " SET "   + AttachmentDescriptor.Write.DELETED_AT + " = NOW()," +
            " "       + AttachmentDescriptor.Write.DELETED_BY_USER_ID + " = :deletedBy" +
            " WHERE " + AttachmentDescriptor.ID.columnName() + " = :id"
    );

    private static final SqlWriteCommand SOFT_DELETE_ALL = SqlWriteCommand.of(
            "UPDATE " + AttachmentDescriptor.Write.TABLE +
            " SET "   + AttachmentDescriptor.Write.DELETED_AT + " = NOW()," +
            " "       + AttachmentDescriptor.Write.DELETED_BY_USER_ID + " = :deletedBy" +
            " WHERE entity_type = 'ADVERTISEMENT'" +
            " AND "  + AttachmentDescriptor.Write.ENTITY_ID + " = :entityId" +
            " AND "  + AttachmentDescriptor.Write.DELETED_AT + " IS NULL"
    );

    private static final SqlWriteCommand RESTORE_DELETE_ALL = SqlWriteCommand.of(
            "UPDATE " + AttachmentDescriptor.Write.TABLE +
            " SET "   + AttachmentDescriptor.Write.DELETED_AT + " = NOW()," +
            " "       + AttachmentDescriptor.Write.DELETED_BY_USER_ID + " = :userId" +
            " WHERE entity_type = 'ADVERTISEMENT'" +
            " AND "  + AttachmentDescriptor.Write.ENTITY_ID + " = :adId" +
            " AND "  + AttachmentDescriptor.Write.DELETED_AT + " IS NULL"
    );

    private static final SqlWriteCommand RESTORE_UNDELETE = SqlWriteCommand.of(
            "UPDATE " + AttachmentDescriptor.Write.TABLE +
            " SET "   + AttachmentDescriptor.Write.DELETED_AT + " = NULL," +
            " "       + AttachmentDescriptor.Write.DELETED_BY_USER_ID + " = NULL" +
            " WHERE entity_type = 'ADVERTISEMENT'" +
            " AND "  + AttachmentDescriptor.Write.ENTITY_ID + " = :adId" +
            " AND "  + AttachmentDescriptor.Write.URL + " = ANY(:urls)"
    );

    private static final SqlWriteCommand RESTORE_MARK_DELETED = SqlWriteCommand.of(
            "UPDATE " + AttachmentDescriptor.Write.TABLE +
            " SET "   + AttachmentDescriptor.Write.DELETED_AT + " = NOW()," +
            " "       + AttachmentDescriptor.Write.DELETED_BY_USER_ID + " = :userId" +
            " WHERE entity_type = 'ADVERTISEMENT'" +
            " AND "  + AttachmentDescriptor.Write.ENTITY_ID + " = :adId" +
            " AND "  + AttachmentDescriptor.Write.DELETED_AT + " IS NULL" +
            " AND NOT (" + AttachmentDescriptor.Write.URL + " = ANY(:urls))"
    );

    private final StorageService                             storageService;
    private final JdbcClient                                 jdbcClient;
    private final PhotoSnapshotService                       photoSnapshotService;
    private final ObjectProvider<AttachmentCurrentUserProvider> currentUserProvider;
    private final ApplicationEventPublisher                  eventPublisher;

    public List<Attachment> getByEntityId(Long entityId) {
        return jdbcClient.sql(
                "SELECT * FROM " + AttachmentDescriptor.TABLE +
                " WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :entityId AND deleted_at IS NULL")
                .paramSource(new MapSqlParameterSource(Attachment.Fields.entityId, entityId))
                .query(ATTACHMENT_PROJECTION).list();
    }

    public Attachment upload(Long entityId, String filename,
                             InputStream inputStream, long contentLength, String contentType) {
        String url = storageService.upload("advertisements/" + entityId, filename, inputStream, contentLength, contentType);
        try {
            Attachment saved = insert(entityId, url, filename, contentType, contentLength);
            capturePhotoChanges(entityId);
            publishMediaUpdate(entityId);
            return saved;
        } catch (Exception e) {
            storageService.delete(url);
            throw e;
        }
    }

    @Transactional
    public void delete(Long attachmentId) {
        Attachment attachment = findById(attachmentId);
        if (attachment == null) return;
        Long userId = resolveCurrentUserId();
        softDelete(attachmentId, userId);
        capturePhotoChanges(attachment.getEntityId());
        publishMediaUpdate(attachment.getEntityId());
    }

    @Transactional
    public void deleteSkipSnapshot(Long attachmentId) {
        Long userId = resolveCurrentUserId();
        softDelete(attachmentId, userId);
    }

    public TempAttachment uploadTemp(String tempSessionId, String filename,
                                     InputStream inputStream, long contentLength, String contentType) {
        String tempUrl = storageService.upload("temp/" + tempSessionId, filename, inputStream, contentLength, contentType);
        return new TempAttachment(tempUrl, filename, contentType, contentLength);
    }

    public void commitTempUploads(Long entityId, List<TempAttachment> temps) {
        commitTempUploadsQuiet(entityId, temps);
        capturePhotoChanges(entityId);
        publishMediaUpdate(entityId);
    }

    public void captureSnapshot(Long entityId) {
        capturePhotoChanges(entityId);
    }

    public void commitTempUploadsQuiet(Long entityId, List<TempAttachment> temps) {
        if (temps.isEmpty()) return;
        String folder = "advertisements/" + entityId;
        List<Attachment> toSave = temps.stream()
                .map(t -> Attachment.builder()
                        .entityId(entityId)
                        .url(storageService.move(t.tempUrl(), folder, t.filename()))
                        .filename(t.filename())
                        .contentType(t.contentType())
                        .size(t.size())
                        .build())
                .toList();
        try {
            toSave.forEach(a -> insert(a.getEntityId(), a.getUrl(), a.getFilename(), a.getContentType(), a.getSize()));
        } catch (Exception e) {
            toSave.forEach(a -> storageService.delete(a.getUrl()));
            throw e;
        }
    }

    @Transactional
    public void restoreToUrls(Long adId, String[] targetUrls, Long userId) {
        if (targetUrls == null || targetUrls.length == 0) {
            RESTORE_DELETE_ALL.execute(jdbcClient,
                    new MapSqlParameterSource().addValue("userId", userId).addValue("adId", adId));
            return;
        }
        // pgjdbc converts String[] to PostgreSQL text[] array automatically
        MapSqlParameterSource urlParams = new MapSqlParameterSource()
                .addValue("adId", adId).addValue("urls", targetUrls);
        RESTORE_UNDELETE.execute(jdbcClient, urlParams);
        RESTORE_MARK_DELETED.execute(jdbcClient,
                new MapSqlParameterSource().addValue("adId", adId).addValue("userId", userId).addValue("urls", targetUrls));
        publishMediaUpdate(adId);
    }

    @Transactional
    public void softDeleteAll(Long entityId, Long deletedByUserId) {
        SOFT_DELETE_ALL.execute(jdbcClient,
                new MapSqlParameterSource().addValue("deletedBy", deletedByUserId).addValue(Attachment.Fields.entityId, entityId));
        publishMediaUpdate(entityId);
    }

    public void discardTempUploads(List<TempAttachment> temps) {
        temps.forEach(t -> storageService.delete(t.tempUrl()));
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void publishMediaUpdate(Long entityId) {
        String mainUrl = jdbcClient.sql(
                "SELECT " + AttachmentDescriptor.Write.URL +
                " FROM " + AttachmentDescriptor.TABLE +
                " WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :entityId" +
                " AND deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")
                .paramSource(new MapSqlParameterSource(Attachment.Fields.entityId, entityId))
                .query((rs, n) -> rs.getString(AttachmentDescriptor.Write.URL))
                .optional().orElse(null);
        Integer count = jdbcClient.sql(
                "SELECT COUNT(*) FROM " + AttachmentDescriptor.TABLE +
                " WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :entityId AND deleted_at IS NULL")
                .paramSource(new MapSqlParameterSource(Attachment.Fields.entityId, entityId))
                .query(Integer.class).single();
        eventPublisher.publishEvent(new AdvertisementMediaUpdatedEvent(entityId, mainUrl, count != null ? count : 0));
    }

    private void capturePhotoChanges(Long entityId) {
        Long userId = resolveCurrentUserId();
        if (userId != null) {
            photoSnapshotService.capture(entityId, userId);
        }
    }

    private Long resolveCurrentUserId() {
        AttachmentCurrentUserProvider p = currentUserProvider.getIfAvailable();
        return p == null ? null : p.getCurrentUserId().orElse(null);
    }

    private Attachment insert(Long entityId, String url, String filename, String contentType, long size) {
        Long id = INSERT_ATTACHMENT.executeAndReturnKey(jdbcClient,
                new MapSqlParameterSource()
                        .addValue(Attachment.Fields.entityId,    entityId)
                        .addValue(Attachment.Fields.url,         url)
                        .addValue(Attachment.Fields.filename,    filename)
                        .addValue(Attachment.Fields.contentType, contentType)
                        .addValue(Attachment.Fields.size,        size));
        return Attachment.builder()
                .id(id).entityId(entityId).url(url).filename(filename)
                .contentType(contentType).size(size).build();
    }

    private void softDelete(Long id, Long deletedBy) {
        SOFT_DELETE.execute(jdbcClient,
                new MapSqlParameterSource().addValue(Attachment.Fields.id, id).addValue("deletedBy", deletedBy));
    }

    private Attachment findById(Long id) {
        return jdbcClient.sql("SELECT * FROM " + AttachmentDescriptor.TABLE + " WHERE id = :id")
                .paramSource(new MapSqlParameterSource(Attachment.Fields.id, id))
                .query(ATTACHMENT_PROJECTION).optional().orElse(null);
    }
}
