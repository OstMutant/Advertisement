package org.ost.provider.repository;

import org.ost.provider.entity.ProviderProfile;
import org.springframework.data.repository.CrudRepository;

/** Trivial save/find for {@link ProviderProfile} -- bespoke queries live in {@code ProviderProfileRepository} instead. */
interface ProviderProfileCrudRepository extends CrudRepository<ProviderProfile, Long> {
}
