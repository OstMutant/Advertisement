package org.ost.attachment.repository;

import org.ost.attachment.entities.Attachment;
import org.springframework.data.repository.CrudRepository;

/** Trivial save/find for {@link Attachment} -- bespoke queries live in {@code AttachmentRepository} instead. */
interface AttachmentCrudRepository extends CrudRepository<Attachment, Long> {
}
