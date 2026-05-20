package org.ost.marketplace.repository.advertisement;

import org.ost.marketplace.entities.Advertisement;
import org.springframework.data.repository.CrudRepository;

interface AdvertisementCrudRepository extends CrudRepository<Advertisement, Long> {
}
