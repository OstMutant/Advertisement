package org.ost.marketplace.repository.advertisement;

import org.ost.marketplace.entities.Advertisement;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdvertisementRepository extends CrudRepository<Advertisement, Long>, AdvertisementRepositoryCustom {

}
