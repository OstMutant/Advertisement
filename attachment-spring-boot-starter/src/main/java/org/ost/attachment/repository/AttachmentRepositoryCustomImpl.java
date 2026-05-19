package org.ost.attachment.repository;

import lombok.RequiredArgsConstructor;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.storage.ConditionalOnAttachmentEnabled;
import org.ost.platform.core.model.EntityType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnAttachmentEnabled
@RequiredArgsConstructor
public class AttachmentRepositoryCustomImpl implements AttachmentRepositoryCustom {

    private final JdbcClient jdbcClient;

    @Override
    public void softDelete(Long id, Long actorId) {
        AttachmentDescriptor.Write.SOFT_DELETE.execute(jdbcClient,
                AttachmentDescriptor.Write.softDeleteParams(id, actorId));
    }

    @Override
    public void softDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        AttachmentDescriptor.Write.SOFT_DELETE_ALL.execute(jdbcClient,
                AttachmentDescriptor.Write.softDeleteAllParams(entityType, entityId, actorId));
    }

    @Override
    public void restoreDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        AttachmentDescriptor.Write.RESTORE_DELETE_ALL.execute(jdbcClient,
                AttachmentDescriptor.Write.softDeleteAllParams(entityType, entityId, actorId));
    }

    @Override
    public void restoreUndelete(EntityType entityType, Long entityId, String[] urls) {
        AttachmentDescriptor.Write.RESTORE_UNDELETE.execute(jdbcClient,
                AttachmentDescriptor.Write.restoreUndeleteParams(entityType, entityId, urls));
    }

    @Override
    public void restoreMarkDeleted(EntityType entityType, Long entityId, Long actorId, String[] urls) {
        AttachmentDescriptor.Write.RESTORE_MARK_DELETED.execute(jdbcClient,
                AttachmentDescriptor.Write.restoreMarkDeletedParams(entityType, entityId, actorId, urls));
    }

    @Override
    public List<Attachment> getByEntityId(EntityType entityType, Long entityId) {
        return jdbcClient.sql(AttachmentDescriptor.Read.SELECT_ACTIVE_BY_ENTITY)
                .paramSource(AttachmentDescriptor.Read.entityParams(entityType, entityId))
                .query(AttachmentDescriptor.Read.PROJECTION).list();
    }

    @Override
    public List<String> getActiveUrls(EntityType entityType, Long entityId) {
        return jdbcClient.sql(AttachmentDescriptor.Read.SELECT_ACTIVE_URLS)
                .paramSource(AttachmentDescriptor.Read.entityParams(entityType, entityId))
                .query(String.class).list();
    }

    @Override
    public List<String> findUrlsDeletedOlderThan(int days) {
        return jdbcClient.sql(AttachmentDescriptor.Read.FIND_URLS_DELETED_OLDER_THAN)
                .paramSource(AttachmentDescriptor.Read.findUrlsDeletedOlderThanParams(days))
                .query(String.class).list();
    }

    @Override
    public int deleteByUrls(List<String> urls) {
        return jdbcClient.sql(AttachmentDescriptor.Write.DELETE_BY_URLS)
                .paramSource(AttachmentDescriptor.Write.deleteByUrlsParams(urls))
                .update();
    }

    @Override
    public MediaStats loadMediaStats(EntityType entityType, Long entityId) {
        record Row(String url, String contentType) {}
        var params = AttachmentDescriptor.Read.entityParams(entityType, entityId);
        var main = jdbcClient.sql(AttachmentDescriptor.Read.SELECT_MAIN_MEDIA)
                .paramSource(params)
                .query((rs, n) -> new Row(rs.getString(AttachmentDescriptor.URL.columnName()),
                                          rs.getString(AttachmentDescriptor.CONTENT_TYPE.columnName())))
                .optional();
        Integer countVal = jdbcClient.sql(AttachmentDescriptor.Read.COUNT_ACTIVE)
                .paramSource(params)
                .query(Integer.class).single();
        int count = countVal != null ? countVal : 0;
        return main
                .map(m -> new MediaStats(m.url(), m.contentType(), count))
                .orElse(new MediaStats(null, null, count));
    }
}
