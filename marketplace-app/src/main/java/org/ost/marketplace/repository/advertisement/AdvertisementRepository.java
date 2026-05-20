package org.ost.marketplace.repository.advertisement;

import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;
import org.ost.marketplace.entities.Advertisement;
import org.ost.platform.attachment.dto.MediaSummaryDto;
import org.ost.sqlengine.FilterableRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AdvertisementRepository extends FilterableRepository<AdvertisementInfoDto, AdvertisementFilterDto> {

    private final AdvertisementCrudRepository crud;

    AdvertisementRepository(JdbcClient jdbcClient, AdvertisementCrudRepository crud) {
        super(jdbcClient, AdvertisementDescriptor.Read.PROJECTION, AdvertisementDescriptor.Read.FILTER);
        this.crud = crud;
    }

    public Advertisement save(Advertisement ad) {
        return crud.save(ad);
    }

    public Optional<Advertisement> findById(Long id) {
        return crud.findById(id);
    }

    public Optional<AdvertisementInfoDto> findAdvertisementById(Long id) {
        return findOneWhere(AdvertisementDescriptor.Read.BY_ID_ACTIVE_WHERE,
                AdvertisementDescriptor.Read.byIdParams(id));
    }

    public void softDelete(Long id, Long deletedByUserId) {
        executeUpdate(AdvertisementDescriptor.Write.SOFT_DELETE,
                AdvertisementDescriptor.Write.softDeleteParams(id, deletedByUserId));
    }

    public void deleteOlderThan(int days) {
        executeUpdate(AdvertisementDescriptor.Write.DELETE_OLDER_THAN,
                AdvertisementDescriptor.Write.deleteOlderThanParams(days));
    }

    public List<Long> findExistingIds(Long[] ids) {
        return findAll(AdvertisementDescriptor.Read.SELECT_EXISTING_IDS,
                AdvertisementDescriptor.Read.existingIdsParams(ids),
                (rs, _) -> rs.getLong("id"));
    }

    public void updateMedia(Long entityId, MediaSummaryDto summary) {
        executeUpdate(AdvertisementDescriptor.Write.UPDATE_MEDIA,
                AdvertisementDescriptor.Write.updateMediaParams(entityId, summary));
    }
}
