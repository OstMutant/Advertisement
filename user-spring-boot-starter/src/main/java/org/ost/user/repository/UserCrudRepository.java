package org.ost.user.repository;

import org.ost.user.entity.User;
import org.springframework.data.repository.CrudRepository;

/** Trivial save/find for {@link User} -- bespoke queries live in {@code UserRepository} instead. */
interface UserCrudRepository extends CrudRepository<User, Long> {
}
