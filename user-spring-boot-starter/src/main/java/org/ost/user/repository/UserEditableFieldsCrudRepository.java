package org.ost.user.repository;

import org.ost.user.entity.UserEditableFields;
import org.springframework.data.repository.CrudRepository;

/**
 * Trivial save/find for {@link UserEditableFields}, used by the profile-edit path's
 * optimistic-locking {@code UPDATE} against the same {@code user_information} table.
 */
interface UserEditableFieldsCrudRepository extends CrudRepository<UserEditableFields, Long> {
}
