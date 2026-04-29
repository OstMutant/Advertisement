package org.ost.attachment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entity.Attachment;
import org.ost.advertisement.events.spi.AttachmentCurrentUserProvider;
import org.ost.advertisement.events.AdvertisementMediaUpdatedEvent;
import org.ost.advertisement.spi.storage.ConditionalOnStorageEnabled;
import org.ost.advertisement.spi.storage.StorageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnStorageEnabled
public class AttachmentService {

    public record TempAttachment(String tempUrl, String filename, String contentType, long size) {}

    private final StorageService                            storageService;
    private final NamedParameterJdbcTemplate                jdbc;
    private final PhotoSnapshotService                      photoSnapshotService;
    private final ObjectProvider<AttachmentCurrentUserProvider> currentUserProvider;
    private final ApplicationEventPublisher                 eventPublisher;

    public List<Attachment> getByEntityId(Long entityId) {
        return jdbc.query(
                "SELECT * FROM attachment WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :id AND deleted_at IS NULL",
                new MapSqlParameterSource("id", entityId),
                this::mapRow
        );
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
            jdbc.update(
                    "UPDATE attachment SET deleted_at = NOW(), deleted_by_user_id = :userId " +
                    "WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId AND deleted_at IS NULL",
                    new MapSqlParameterSource().addValue("userId", userId).addValue("adId", adId)
            );
            return;
        }
        var urlArray = jdbc.getJdbcOperations().execute(
                (java.sql.Connection conn) -> conn.createArrayOf("text", targetUrls)
        );
        jdbc.update(
                "UPDATE attachment SET deleted_at = NULL, deleted_by_user_id = NULL " +
                "WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId AND url = ANY(:urls)",
                new MapSqlParameterSource().addValue("adId", adId).addValue("urls", urlArray)
        );
        jdbc.update(
                "UPDATE attachment SET deleted_at = NOW(), deleted_by_user_id = :userId " +
                "WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId " +
                "  AND deleted_at IS NULL AND NOT (url = ANY(:urls))",
                new MapSqlParameterSource().addValue("adId", adId).addValue("userId", userId).addValue("urls", urlArray)
        );
        publishMediaUpdate(adId);
    }

    @Transactional
    public void softDeleteAll(Long entityId, Long deletedByUserId) {
        jdbc.update(
                "UPDATE attachment SET deleted_at = NOW(), deleted_by_user_id = :deletedBy " +
                "WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :entityId AND deleted_at IS NULL",
                new MapSqlParameterSource().addValue("deletedBy", deletedByUserId).addValue("entityId", entityId)
        );
        publishMediaUpdate(entityId);
    }

    public void discardTempUploads(List<TempAttachment> temps) {
        temps.forEach(t -> storageService.delete(t.tempUrl()));
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void publishMediaUpdate(Long entityId) {
        String mainUrl = jdbc.query(
                "SELECT url FROM attachment WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :id " +
                "AND deleted_at IS NULL ORDER BY created_at ASC LIMIT 1",
                new MapSqlParameterSource("id", entityId),
                (rs, n) -> rs.getString("url")
        ).stream().findFirst().orElse(null);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM attachment WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :id AND deleted_at IS NULL",
                new MapSqlParameterSource("id", entityId),
                Integer.class
        );
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
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(
                "INSERT INTO attachment (entity_type, entity_id, url, filename, content_type, size, created_at) " +
                "VALUES ('ADVERTISEMENT', :entityId, :url, :filename, :contentType, :size, NOW())",
                new MapSqlParameterSource()
                        .addValue("entityId", entityId)
                        .addValue("url", url)
                        .addValue("filename", filename)
                        .addValue("contentType", contentType)
                        .addValue("size", size),
                keyHolder,
                new String[]{"id"}
        );
        Long id = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
        return Attachment.builder()
                .id(id).entityId(entityId).url(url).filename(filename)
                .contentType(contentType).size(size).build();
    }

    private void softDelete(Long id, Long deletedBy) {
        jdbc.update(
                "UPDATE attachment SET deleted_at = NOW(), deleted_by_user_id = :deletedBy WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("deletedBy", deletedBy)
        );
    }

    private Attachment findById(Long id) {
        return jdbc.query(
                "SELECT * FROM attachment WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow
        ).stream().findFirst().orElse(null);
    }

    private Attachment mapRow(ResultSet rs, int rowNum) throws SQLException {
        Instant deletedAt = rs.getTimestamp("deleted_at") != null
                ? rs.getTimestamp("deleted_at").toInstant() : null;
        Instant createdAt = rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toInstant() : null;
        return Attachment.builder()
                .id(rs.getLong("id"))
                .entityId(rs.getLong("entity_id"))
                .url(rs.getString("url"))
                .filename(rs.getString("filename"))
                .contentType(rs.getString("content_type"))
                .size(rs.getLong("size"))
                .createdAt(createdAt)
                .deletedAt(deletedAt)
                .deletedByUserId(rs.getObject("deleted_by_user_id", Long.class))
                .build();
    }
}
