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

import static org.ost.marketplace.repository.advertisement.AdvertisementDescriptor.*;

@Repository
public class AdvertisementRepository extends FilterableRepository<AdvertisementInfoDto, AdvertisementFilterDto> {

    private final AdvertisementCrudRepository crud;

    AdvertisementRepository(JdbcClient jdbcClient, AdvertisementCrudRepository crud) {
        super(jdbcClient, Read.PROJECTION, Read.FILTER);
        this.crud = crud;
    }

    public Advertisement save(Advertisement ad) {
        return crud.save(ad);
    }

    public Optional<Advertisement> findById(Long id) {
        return crud.findById(id);
    }

    public Optional<AdvertisementInfoDto> findAdvertisementById(Long id) {
        return findOneWhere(Read.BY_ID_ACTIVE_WHERE, Read.byIdParams(id));
    }

    public void softDelete(Long id, Long deletedByUserId) {
        executeUpdate(Write.SOFT_DELETE, Write.softDeleteParams(id, deletedByUserId));
    }

    public void deleteOlderThan(int days) {
        executeUpdate(Write.DELETE_OLDER_THAN, Write.deleteOlderThanParams(days));
    }

    public List<Long> findExistingIds(Long[] ids) {
        return findAll(Read.SELECT_EXISTING_IDS, Read.existingIdsParams(ids),
                (rs, _) -> ID.extract(rs));
    }

    public void updateMedia(Long entityId, MediaSummaryDto summary) {
        executeUpdate(Write.UPDATE_MEDIA, Write.updateMediaParams(entityId, summary));
    }
}
