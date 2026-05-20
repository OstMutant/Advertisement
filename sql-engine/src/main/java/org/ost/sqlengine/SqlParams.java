package org.ost.sqlengine;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SqlParams {

    public static MapSqlParameterSource of(String key, Object value) {
        return new MapSqlParameterSource(key, value);
    }

    public static Builder with(String key, Object value) {
        return new Builder().add(key, value);
    }

    public static final class Builder {
        private final MapSqlParameterSource source = new MapSqlParameterSource();

        private Builder() {}

        public Builder add(String name, Object value) {
            source.addValue(name, value);
            return this;
        }

        public MapSqlParameterSource build() {
            return source;
        }
    }
}
