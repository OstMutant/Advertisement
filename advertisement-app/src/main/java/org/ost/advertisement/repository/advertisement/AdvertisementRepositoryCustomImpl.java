package org.ost.advertisement.repository.advertisement;

import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.sqlengine.RepositoryCustom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AdvertisementRepositoryCustomImpl
        extends RepositoryCustom<AdvertisementInfoDto, AdvertisementFilterDto>
        implements AdvertisementRepositoryCustom {

    private static final AdvertisementProjection   ADVERTISEMENT_PROJECTION    = new AdvertisementProjection();
    private static final AdvertisementFilterBuilder ADVERTISEMENT_FILTER_BUILDER = new AdvertisementFilterBuilder();

    public AdvertisementRepositoryCustomImpl(JdbcClient jdbcClient) {
        super(jdbcClient, ADVERTISEMENT_PROJECTION, ADVERTISEMENT_FILTER_BUILDER);
    }

    @Override
    public void softDelete(Long id, Long deletedByUserId) {
        executor.jdbcClient().sql(
                "UPDATE advertisement SET deleted_at = NOW(), deleted_by_user_id = :deletedBy WHERE id = :id")
                .paramSource(new MapSqlParameterSource().addValue("id", id).addValue("deletedBy", deletedByUserId))
                .update();
    }

    @Override
    public Optional<AdvertisementInfoDto> findAdvertisementById(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        String sql = sqlQueryBuilder.select(
                sqlProjection.getSelectClause(),
                sqlProjection.getSqlSource(),
                "a.id = :id AND a.deleted_at IS NULL"
        );
        return executor.findOne(sql, params, sqlProjection);
    }
}
