package org.ost.advertisement.repository;

import org.ost.advertisement.entity.Advertisement;
import org.springframework.data.repository.CrudRepository;

interface AdvertisementCrudRepository extends CrudRepository<Advertisement, Long> {
}
