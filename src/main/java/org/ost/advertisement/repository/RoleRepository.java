package org.ost.advertisement.repository;

import org.ost.advertisement.entyties.Role;
import org.springframework.data.repository.CrudRepository;

public interface RoleRepository extends CrudRepository<Role, Long> {

	Role findByCode(String code);
}

