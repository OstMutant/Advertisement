package org.ost.attachment.repository;

import org.ost.attachment.entities.Attachment;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttachmentRepository
        extends CrudRepository<Attachment, Long>, AttachmentRepositoryCustom {
}
