// UserRepository.java
package org.ost.advertisement.repository; // Update package if different

import org.ost.advertisement.entyties.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends CrudRepository<User, Long>, UserRepositoryCustom {
	// This interface will contain basic CRUD operations provided by CrudRepository
	// and extend UserRepositoryCustom for custom query methods.
}
