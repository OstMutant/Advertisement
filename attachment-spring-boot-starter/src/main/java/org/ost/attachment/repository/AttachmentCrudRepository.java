package org.ost.attachment.repository;

import org.ost.attachment.entities.Attachment;
import org.springframework.data.repository.CrudRepository;

interface AttachmentCrudRepository extends CrudRepository<Attachment, Long> {
}
