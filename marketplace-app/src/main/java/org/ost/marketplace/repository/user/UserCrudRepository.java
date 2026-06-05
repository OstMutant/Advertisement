package org.ost.marketplace.repository.user;

import org.ost.marketplace.entities.User;
import org.springframework.data.repository.CrudRepository;

interface UserCrudRepository extends CrudRepository<User, Long> {
}
