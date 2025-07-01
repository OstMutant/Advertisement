// AdvertisementRepository.java
package org.ost.advertisement.repository;

import org.ost.advertisement.entyties.Advertisement;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdvertisementRepository extends CrudRepository<Advertisement, Long>, AdvertisementRepositoryCustom {
	// This interface will contain basic CRUD operations provided by CrudRepository
	// and extend AdvertisementRepositoryCustom for custom query methods.
}
