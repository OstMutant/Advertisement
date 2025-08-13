package org.ost.advertisement.repository;

import org.ost.advertisement.entities.Advertisement;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdvertisementRepository extends CrudRepository<Advertisement, Long>, AdvertisementRepositoryCustom {

}
