package org.ost.advertisement.repository;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.filter.UserFilter;
import org.ost.advertisement.entities.User;
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
		if (filter == null) {
			return Criteria.empty();
		}

		return new CriteriaBuilder()
			.like("name", filter.getName())
			.like("email", filter.getEmail())
			.equal("role", filter.getRole() != null ? filter.getRole().name() : null)
			.range("created_at", filter.getCreatedAtStart(), filter.getCreatedAtEnd())
			.range("updated_at", filter.getUpdatedAtStart(), filter.getUpdatedAtEnd())
			.range("id", filter.getStartId(), filter.getEndId())
			.build();
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
