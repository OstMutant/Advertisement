package org.ost.sqlengine;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Factory for {@link MapSqlParameterSource} instances used in repository param methods.
 * <ul>
 *   <li>{@code of(key, value)} — single-param shorthand, returns a plain {@link MapSqlParameterSource}.</li>
 *   <li>{@code with(key, value).add(...).add(...)} — fluent builder for multi-param maps;
 *       {@link Builder} IS a {@link MapSqlParameterSource} so no terminal call is needed.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SqlParams {

    public static MapSqlParameterSource of(String key, Object value) {
        return new MapSqlParameterSource(key, value);
    }

    public static Builder with(String key, Object value) {
        return new Builder().add(key, value);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder extends MapSqlParameterSource {

        public Builder add(String name, Object value) {
            addValue(name, value);
            return this;
        }
    }
}
