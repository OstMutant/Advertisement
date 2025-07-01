// UserRepositoryCustom.java
package org.ost.advertisement.repository; // Update package if different

import java.util.List;
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.User;
import org.springframework.data.domain.Pageable;

public interface UserRepositoryCustom {
    /**
     * Finds users based on a filter and pagination information.
     * @param filter The UserFilter object containing criteria for filtering.
     * @param pageable The Pageable object containing pagination and sorting information.
     * @return A List of User objects matching the criteria.
     */
    List<User> findByFilter(UserFilter filter, Pageable pageable); // Changed from Flux

    /**
     * Counts the total number of users matching a given filter.
     * This is used for pagination metadata.
     * @param filter The UserFilter object containing criteria for filtering.
     * @return A Long representing the total count of matching users.
     */
    Long countByFilter(UserFilter filter); // Changed from Mono<Long>
}
