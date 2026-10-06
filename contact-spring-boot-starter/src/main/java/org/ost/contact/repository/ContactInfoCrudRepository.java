package org.ost.contact.repository;

import org.ost.contact.entity.ContactInfo;
import org.springframework.data.repository.CrudRepository;

/** Trivial save/find for {@link ContactInfo} -- bespoke queries live in {@code ContactRepository} instead. */
interface ContactInfoCrudRepository extends CrudRepository<ContactInfo, Long> {
}
