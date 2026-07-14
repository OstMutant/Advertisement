package org.ost.user.repository;

import org.ost.user.entity.UserProfileUpdate;
import org.springframework.data.repository.CrudRepository;

interface UserProfileCrudRepository extends CrudRepository<UserProfileUpdate, Long> {
}
