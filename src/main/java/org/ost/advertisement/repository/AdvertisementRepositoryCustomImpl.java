package org.ost.advertisement.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementFilter;
import org.ost.advertisement.entyties.Advertisement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdvertisementRepositoryCustomImpl implements AdvertisementRepositoryCustom {

	private final JdbcAggregateTemplate template;

	private Criteria buildCriteria(AdvertisementFilter filter) {
        if (filter == null) {
            return Criteria.empty();
        }

		return new CriteriaBuilder()
			.like("title", filter.getTitleFilter())
			.equal("category", filter.getCategoryFilter())
			.like("location", filter.getLocationFilter())
			.equal("status", filter.getStatusFilter())
			.range("created_at", filter.getCreatedAtStart(), filter.getCreatedAtEnd())
			.range("updated_at", filter.getUpdatedAtStart(), filter.getUpdatedAtEnd())
			.range("id", filter.getStartId(), filter.getEndId())
			.build();
	}


	@Override
	public List<Advertisement> findByFilter(AdvertisementFilter filter, Pageable pageable) {
		Criteria finalCriteria = buildCriteria(filter);
		Query query = Query.query(finalCriteria).with(pageable);
		return template.findAll(query, Advertisement.class);
	}

	@Override
	public Long countByFilter(AdvertisementFilter filter) {
		Criteria finalCriteria = buildCriteria(filter);
		Query query = Query.query(finalCriteria);
		return template.count(query, Advertisement.class);
	}
}
