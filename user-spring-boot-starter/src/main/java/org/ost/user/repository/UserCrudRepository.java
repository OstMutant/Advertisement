package org.ost.user.repository;

import org.ost.user.entity.User;
import org.springframework.data.repository.CrudRepository;

interface UserCrudRepository extends CrudRepository<User, Long> {
}
