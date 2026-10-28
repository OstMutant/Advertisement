package org.ost.contact.repository;

import org.ost.contact.entity.ContactView;
import org.springframework.data.repository.CrudRepository;

/** Trivial insert for {@link ContactView} -- the per-channel monthly count query lives in {@code ContactRepository} instead. */
interface ContactViewCrudRepository extends CrudRepository<ContactView, Long> {
}
