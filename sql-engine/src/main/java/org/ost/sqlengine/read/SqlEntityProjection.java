package org.ost.sqlengine.read;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.ost.sqlengine.common.SqlDescriptorField;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Projection for entity queries that can be assembled dynamically (filter, sort, pagination).
 * Carries the FROM source ({@code sqlSource}) used for SELECT and an optional separate
 * {@code countSource} used for COUNT(*) — useful when the SELECT source contains joins
 * that would inflate the count.
 */
public class SqlEntityProjection<T> extends SqlBaseProjection<T> {

    @Getter private final String sqlSource;
    @Getter private final String countSource;
    private final RowMapper<T> mapper;

    public SqlEntityProjection(List<SqlDescriptorField<?>> items, String sqlSource, RowMapper<T> mapper) {
        this(items, sqlSource, sqlSource, mapper);
    }

    public SqlEntityProjection(List<SqlDescriptorField<?>> items, String sqlSource, String countSource, RowMapper<T> mapper) {
        Objects.requireNonNull(sqlSource,   "Parameter 'sqlSource' must not be null.");
        Objects.requireNonNull(countSource, "Parameter 'countSource' must not be null.");
        super(items);
        this.sqlSource   = sqlSource;
        this.countSource = countSource;
        this.mapper      = mapper;
    }

    @Override
    public T mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
        return mapper.mapRow(rs, rowNum);
    }

    public static <T> SqlEntityProjection<T> of(List<SqlDescriptorField<?>> items, String sqlSource, RowMapper<T> mapper) {
        return new SqlEntityProjection<>(items, sqlSource, mapper);
    }

    public static <T> SqlEntityProjection<T> of(List<SqlDescriptorField<?>> items, String sqlSource, String countSource, RowMapper<T> mapper) {
        return new SqlEntityProjection<>(items, sqlSource, countSource, mapper);
    }
}
