package org.ost.attachment.repository;

import org.ost.platform.core.model.EntityType;

import java.util.List;

public interface AttachmentRepositoryCustom {

    record MediaStats(String mainUrl, String mainContentType, int count) {}

    void softDelete(Long id, Long actorId);

    void softDeleteAll(EntityType entityType, Long entityId, Long actorId);

    void restoreDeleteAll(EntityType entityType, Long entityId, Long actorId);

    void restoreUndelete(EntityType entityType, Long entityId, String[] urls);

    void restoreMarkDeleted(EntityType entityType, Long entityId, Long actorId, String[] urls);

    List<org.ost.attachment.entities.Attachment> getByEntityId(EntityType entityType, Long entityId);

    List<String> getActiveUrls(EntityType entityType, Long entityId);

    List<String> findUrlsDeletedOlderThan(int days);

    int deleteByUrls(List<String> urls);

    MediaStats loadMediaStats(EntityType entityType, Long entityId);
}
