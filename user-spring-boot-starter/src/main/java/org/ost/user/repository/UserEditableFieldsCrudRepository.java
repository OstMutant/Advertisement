package org.ost.user.repository;

import org.ost.user.entity.UserEditableFields;
import org.springframework.data.repository.CrudRepository;

interface UserEditableFieldsCrudRepository extends CrudRepository<UserEditableFields, Long> {
}
