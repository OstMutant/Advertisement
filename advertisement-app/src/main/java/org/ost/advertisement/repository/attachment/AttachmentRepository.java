package org.ost.advertisement.repository.attachment;

import org.ost.advertisement.entities.Attachment;
import org.ost.advertisement.entities.EntityType;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachmentRepository extends CrudRepository<Attachment, Long> {

    List<Attachment> findByEntityTypeAndEntityIdAndDeletedAtIsNull(EntityType entityType, Long entityId);

    void deleteByEntityTypeAndEntityId(EntityType entityType, Long entityId);
}
