// UserRepositoryCustomImpl.java
package org.ost.advertisement.repository; // Update package if different

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.UserFilter;
import org.ost.advertisement.entyties.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    // Changed from R2dbcEntityTemplate to JdbcAggregateTemplate
    private final JdbcAggregateTemplate template;

    /**
     * Builds the criteria for filtering users based on the UserFilter object.
     * This method is private as it's a helper for the repository methods.
     *
     * @param filter The filter criteria to apply.
     * @return The constructed Criteria object.
     */
    private Criteria buildCriteria(UserFilter filter) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (filter != null) {
            // Filtering by nameFilter (partial match, case-insensitive)
            if (filter.getNameFilter() != null && !filter.getNameFilter().isBlank()) {
                criteriaList.add(Criteria.where("name").like("%" + filter.getNameFilter().toLowerCase() + "%").ignoreCase(true));
            }

            // Filtering by createdAt range
            if (filter.getCreatedAtStart() != null) {
                criteriaList.add(Criteria.where("created_at").greaterThanOrEquals(filter.getCreatedAtStart()));
            }
            if (filter.getCreatedAtEnd() != null) {
                criteriaList.add(Criteria.where("created_at").lessThanOrEquals(filter.getCreatedAtEnd()));
            }

            // Filtering by updatedAt range
            if (filter.getUpdatedAtStart() != null) {
                criteriaList.add(Criteria.where("updated_at").greaterThanOrEquals(filter.getUpdatedAtStart()));
            }
            if (filter.getUpdatedAtEnd() != null) {
                criteriaList.add(Criteria.where("updated_at").lessThanOrEquals(filter.getUpdatedAtEnd()));
            }

            // Filtering by ID range
            if (filter.getStartId() != null && filter.getStartId() > 0) {
                criteriaList.add(Criteria.where("id").greaterThanOrEquals(filter.getStartId()));
            }
            if (filter.getEndId() != null && filter.getEndId() > 0) {
                criteriaList.add(Criteria.where("id").lessThanOrEquals(filter.getEndId()));
            }
        }
        // Combine all criteria with AND. If criteriaList is empty, Criteria.empty() is returned.
        return criteriaList.stream().reduce(Criteria.empty(), Criteria::and);
    }

    @Override
    public List<User> findByFilter(UserFilter filter, Pageable pageable) { // Changed return type from Flux to List
        // Build criteria using the helper method
        Criteria finalCriteria = buildCriteria(filter);
        // Create query with criteria and pagination
        Query query = Query.query(finalCriteria).with(pageable);

        // Execute the select query
        return template.findAll(query, User.class); // Explicit cast for clarity
    }

    /**
     * Counts the number of users matching the given filter criteria.
     *
     * @param filter The filter criteria to apply.
     * @return A Long representing the total count of matching users.
     */
    @Override
    public Long countByFilter(UserFilter filter) { // Changed return type from Mono<Long> to Long
        // Build criteria using the helper method
        Criteria finalCriteria = buildCriteria(filter);
        // Create query with criteria (no pagination needed for count)
        Query query = Query.query(finalCriteria);

        // Execute the count query
        return template.count(query, User.class);
    }
}
