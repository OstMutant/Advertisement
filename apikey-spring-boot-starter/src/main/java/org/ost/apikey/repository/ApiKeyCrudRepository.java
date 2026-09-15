package org.ost.apikey.repository;

import org.ost.apikey.entity.ApiKey;
import org.springframework.data.repository.CrudRepository;

/** Trivial save/find for {@link ApiKey} -- bespoke queries live in {@code ApiKeyRepository} instead. */
interface ApiKeyCrudRepository extends CrudRepository<ApiKey, Long> {
}
