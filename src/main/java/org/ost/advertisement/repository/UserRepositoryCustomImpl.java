package org.ost.advertisement.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

	private final JdbcAggregateTemplate template;

	private Criteria buildCriteria(UserFilter filter) {
		List<Criteria> criteriaList = new ArrayList<>();

		if (filter != null) {
			if (filter.getNameFilter() != null && !filter.getNameFilter().isBlank()) {
				criteriaList.add(
					Criteria.where("name").like("%" + filter.getNameFilter().toLowerCase() + "%").ignoreCase(true));
			}

			if (filter.getCreatedAtStart() != null) {
				criteriaList.add(Criteria.where("created_at").greaterThanOrEquals(filter.getCreatedAtStart()));
			}
			if (filter.getCreatedAtEnd() != null) {
				criteriaList.add(Criteria.where("created_at").lessThanOrEquals(filter.getCreatedAtEnd()));
			}

			if (filter.getUpdatedAtStart() != null) {
				criteriaList.add(Criteria.where("updated_at").greaterThanOrEquals(filter.getUpdatedAtStart()));
			}
			if (filter.getUpdatedAtEnd() != null) {
				criteriaList.add(Criteria.where("updated_at").lessThanOrEquals(filter.getUpdatedAtEnd()));
			}

			if (filter.getStartId() != null && filter.getStartId() > 0) {
				criteriaList.add(Criteria.where("id").greaterThanOrEquals(filter.getStartId()));
			}
			if (filter.getEndId() != null && filter.getEndId() > 0) {
				criteriaList.add(Criteria.where("id").lessThanOrEquals(filter.getEndId()));
			}
		}
		return criteriaList.stream().reduce(Criteria.empty(), Criteria::and);
	}

	@Override
	public List<User> findByFilter(UserFilter filter, Pageable pageable) {
		Criteria finalCriteria = buildCriteria(filter);
		Query query = Query.query(finalCriteria).with(pageable);

		return template.findAll(query, User.class);
	}

	@Override
	public Long countByFilter(UserFilter filter) {
		Criteria finalCriteria = buildCriteria(filter);
		Query query = Query.query(finalCriteria);

		return template.count(query, User.class);
	}

	@Override
	public Optional<User> findByEmail(String email) {
		Criteria criteria = Criteria.where("email").is(email);
		Query query = Query.query(criteria);
		return template.findOne(query, User.class);
	}
}
