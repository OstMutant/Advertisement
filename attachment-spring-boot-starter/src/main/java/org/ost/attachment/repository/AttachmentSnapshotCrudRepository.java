package org.ost.attachment.repository;

import org.ost.attachment.entities.AttachmentSnapshot;
import org.springframework.data.repository.CrudRepository;

interface AttachmentSnapshotCrudRepository extends CrudRepository<AttachmentSnapshot, Long> {
}
