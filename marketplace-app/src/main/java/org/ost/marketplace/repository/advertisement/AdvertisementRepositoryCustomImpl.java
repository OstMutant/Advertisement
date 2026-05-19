package org.ost.marketplace.repository.advertisement;

import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;
import org.ost.sqlengine.RepositoryCustom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AdvertisementRepositoryCustomImpl
        extends RepositoryCustom<AdvertisementInfoDto, AdvertisementFilterDto>
        implements AdvertisementRepositoryCustom {

    public AdvertisementRepositoryCustomImpl(JdbcClient jdbcClient) {
        super(jdbcClient, AdvertisementDescriptor.Read.PROJECTION, AdvertisementDescriptor.Read.FILTER);
    }

    @Override
    public void softDelete(Long id, Long deletedByUserId) {
        execute(AdvertisementDescriptor.Write.SOFT_DELETE,
                AdvertisementDescriptor.Write.softDeleteParams(id, deletedByUserId));
    }

    @Override
    public Optional<AdvertisementInfoDto> findAdvertisementById(Long id) {
        return findOne(AdvertisementDescriptor.Read.WHERE_BY_ID_ACTIVE,
                AdvertisementDescriptor.Read.byIdParams(id));
    }

    @Override
    public int deleteOlderThan(int days) {
        return execute(AdvertisementDescriptor.Write.DELETE_OLDER_THAN,
                AdvertisementDescriptor.Write.deleteOlderThanParams(days));
    }
}
