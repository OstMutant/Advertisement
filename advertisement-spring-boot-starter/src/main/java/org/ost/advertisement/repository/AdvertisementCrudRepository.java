package org.ost.advertisement.repository;

import org.ost.advertisement.entity.Advertisement;
import org.springframework.data.repository.CrudRepository;

/** Trivial save/findById CRUD for {@code Advertisement} entities; bespoke filter/sort/pagination queries live in {@link AdvertisementRepository}. */
interface AdvertisementCrudRepository extends CrudRepository<Advertisement, Long> {
}
