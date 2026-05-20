package org.ost.sqlengine.read;

import lombok.Getter;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public abstract class SqlEntityProjection<T> extends SqlBaseProjection<T> {

    @Getter private final String sqlSource;
    @Getter private final String countSource;

    protected SqlEntityProjection(List<SqlSelectField<?>> items, String sqlSource) {
        this(items, sqlSource, sqlSource);
    }

    protected SqlEntityProjection(List<SqlSelectField<?>> items, String sqlSource, String countSource) {
        Objects.requireNonNull(sqlSource,    "Parameter 'sqlSource' must not be null.");
        Objects.requireNonNull(countSource,  "Parameter 'countSource' must not be null.");
        super(items);
        this.sqlSource   = sqlSource;
        this.countSource = countSource;
    }

    public static <T> SqlEntityProjection<T> of(List<SqlSelectField<?>> items, String sqlSource, RowMapper<T> mapper) {
        return new SqlEntityProjection<>(items, sqlSource) {
            @Override
            public T mapRow(ResultSet rs, int rowNum) throws SQLException {
                return mapper.mapRow(rs, rowNum);
            }
        };
    }

    public static <T> SqlEntityProjection<T> of(List<SqlSelectField<?>> items, String sqlSource, String countSource, RowMapper<T> mapper) {
        return new SqlEntityProjection<>(items, sqlSource, countSource) {
            @Override
            public T mapRow(ResultSet rs, int rowNum) throws SQLException {
                return mapper.mapRow(rs, rowNum);
            }
        };
    }
}
