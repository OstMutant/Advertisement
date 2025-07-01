package org.ost.advertisement.repository;

import java.util.ArrayList;
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
        List<Criteria> criteriaList = new ArrayList<>();

        if (filter != null) {
            if (filter.getTitleFilter() != null && !filter.getTitleFilter().isBlank()) {
                criteriaList.add(Criteria.where("title").like("%" + filter.getTitleFilter().toLowerCase() + "%").ignoreCase(true));
            }

            if (filter.getCategoryFilter() != null && !filter.getCategoryFilter().isBlank()) {
                criteriaList.add(Criteria.where("category").is(filter.getCategoryFilter()).ignoreCase(true));
            }

            if (filter.getLocationFilter() != null && !filter.getLocationFilter().isBlank()) {
                criteriaList.add(Criteria.where("location").like("%" + filter.getLocationFilter().toLowerCase() + "%").ignoreCase(true));
            }

            if (filter.getStatusFilter() != null && !filter.getStatusFilter().isBlank()) {
                criteriaList.add(Criteria.where("status").is(filter.getStatusFilter()).ignoreCase(true));
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
