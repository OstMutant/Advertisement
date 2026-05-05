package org.ost.advertisement.repository.advertisement;

import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.sqlengine.RepositoryCustom;
import org.ost.sqlengine.writer.SqlFixedWriter;
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

    private static final SqlFixedWriter SOFT_DELETE = SqlFixedWriter.of(
            "UPDATE " + AdvertisementProjection.Write.TABLE +
            " SET " + AdvertisementProjection.Write.DELETED_AT + " = NOW()," +
            " "     + AdvertisementProjection.Write.DELETED_BY_USER_ID + " = :deletedBy" +
            " WHERE id = :id"
    );

    public AdvertisementRepositoryCustomImpl(JdbcClient jdbcClient) {
        super(jdbcClient, ADVERTISEMENT_PROJECTION, ADVERTISEMENT_FILTER_BUILDER);
    }

    @Override
    public void softDelete(Long id, Long deletedByUserId) {
        executor.execute(SOFT_DELETE.sql(),
                new MapSqlParameterSource().addValue("id", id).addValue("deletedBy", deletedByUserId));
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
