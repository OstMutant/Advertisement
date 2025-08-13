package org.ost.advertisement.repository;

import org.ost.advertisement.entities.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends CrudRepository<User, Long>, UserRepositoryCustom {
}
