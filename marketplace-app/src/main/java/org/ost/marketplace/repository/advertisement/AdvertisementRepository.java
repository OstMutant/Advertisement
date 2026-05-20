package org.ost.marketplace.repository.advertisement;

import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;
import org.ost.marketplace.entities.Advertisement;
import org.ost.sqlengine.FilterableRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AdvertisementRepository {

    private final FilterableRepository<AdvertisementInfoDto, AdvertisementFilterDto> query;
    private final AdvertisementCrudRepository crud;

    AdvertisementRepository(JdbcClient jdbcClient, AdvertisementCrudRepository crud) {
        this.query = new FilterableRepository<>(jdbcClient,
                AdvertisementDescriptor.Read.PROJECTION,
                AdvertisementDescriptor.Read.FILTER);
        this.crud  = crud;
    }

    public Advertisement save(Advertisement ad) {
        return crud.save(ad);
    }

    public Optional<Advertisement> findById(Long id) {
        return crud.findById(id);
    }

    public List<AdvertisementInfoDto> findByFilter(AdvertisementFilterDto filter, Pageable pageable) {
        return query.findByFilter(filter, pageable);
    }

    public Long countByFilter(AdvertisementFilterDto filter) {
        return query.countByFilter(filter);
    }

    public Optional<AdvertisementInfoDto> findAdvertisementById(Long id) {
        return query.findOneWhere(AdvertisementDescriptor.Read.BY_ID_ACTIVE_WHERE,
                AdvertisementDescriptor.Read.byIdParams(id));
    }

    public void softDelete(Long id, Long deletedByUserId) {
        query.executeUpdate(AdvertisementDescriptor.Write.SOFT_DELETE,
                AdvertisementDescriptor.Write.softDeleteParams(id, deletedByUserId));
    }

    public void deleteOlderThan(int days) {
        query.executeUpdate(AdvertisementDescriptor.Write.DELETE_OLDER_THAN,
                AdvertisementDescriptor.Write.deleteOlderThanParams(days));
    }
}
