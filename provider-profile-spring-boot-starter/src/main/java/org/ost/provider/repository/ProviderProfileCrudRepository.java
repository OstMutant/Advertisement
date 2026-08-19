package org.ost.provider.repository;

import org.ost.provider.entity.ProviderProfile;
import org.springframework.data.repository.CrudRepository;

interface ProviderProfileCrudRepository extends CrudRepository<ProviderProfile, Long> {
}
