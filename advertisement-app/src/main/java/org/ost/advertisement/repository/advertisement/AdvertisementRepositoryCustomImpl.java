package org.ost.advertisement.repository.advertisement;

import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.sqlengine.RepositoryCustom;
import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AdvertisementRepositoryCustomImpl
        extends RepositoryCustom<AdvertisementInfoDto, AdvertisementFilterDto>
        implements AdvertisementRepositoryCustom {

    private static final AdvertisementDescriptor    PROJECTION    = new AdvertisementDescriptor();
    private static final AdvertisementFilterBuilder FILTER_BUILDER = new AdvertisementFilterBuilder();

    private static final String BY_ID = "a.id = :id AND a.deleted_at IS NULL";

    private static final SqlWriteCommand SOFT_DELETE = SqlWriteCommand.of(
            "UPDATE " + AdvertisementDescriptor.Write.TABLE +
            " SET " + AdvertisementDescriptor.Write.DELETED_AT + " = NOW()," +
            " "     + AdvertisementDescriptor.Write.DELETED_BY_USER_ID + " = :deletedBy" +
            " WHERE id = :id"
    );

    public AdvertisementRepositoryCustomImpl(JdbcClient jdbcClient) {
        super(jdbcClient, PROJECTION, FILTER_BUILDER);
    }

    @Override
    public void softDelete(Long id, Long deletedByUserId) {
        execute(SOFT_DELETE,
                new MapSqlParameterSource().addValue("id", id).addValue("deletedBy", deletedByUserId));
    }

    @Override
    public Optional<AdvertisementInfoDto> findAdvertisementById(Long id) {
        return findOne(BY_ID, new MapSqlParameterSource("id", id));
    }

    @Override
    public int deleteOlderThan(int days) {
        return execute(
                "DELETE FROM " + AdvertisementDescriptor.Write.TABLE +
                " WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)",
                new MapSqlParameterSource("days", days));
    }
}
