package org.ost.sqlengine;

import org.ost.sqlengine.filter.SqlFilterBuilder;
import org.ost.sqlengine.read.SqlEntityProjection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

public final class FilterableRepository<T, F> extends RepositoryCustom<T, F> {

    public FilterableRepository(JdbcClient jdbcClient,
                                 SqlEntityProjection<T> projection,
                                 SqlFilterBuilder<F> filterBuilder) {
        super(jdbcClient, projection, filterBuilder);
    }

    @Override
    public Optional<T> findOne(String where, MapSqlParameterSource params) {
        return super.findOne(where, params);
    }
}
