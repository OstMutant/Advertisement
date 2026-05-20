package org.ost.sqlengine;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Contract for classes that describe an entity table on both sides — reads and writes.
 *
 * <p>Conventional layout:
 * <ul>
 *   <li>Public column-name constants and {@code SqlDescriptorField} fields at the top
 *       (shared between {@code Read} and {@code Write}).</li>
 *   <li>{@code public static final class Read} — {@code PROJECTION} for full-row
 *       mapping, {@code SELECT_*} SQL constants, and param-factory methods.</li>
 *   <li>{@code public static final class Write} — {@code SqlCommand} constants
 *       and matching param-factory methods.</li>
 * </ul>
 *
 * <p>Implementations should be {@code final} with a private constructor — descriptors
 * are pure namespaces, not entities.
 *
 * <p>Use {@link Params} to build named-parameter maps in param-factory methods.
 */
public interface SqlEntityDescriptor {

    /**
     * Factory for {@link MapSqlParameterSource} instances used in descriptor param-factory methods.
     * <ul>
     *   <li>{@code of(key, value)} — single-param shorthand.</li>
     *   <li>{@code with(key, value).add(...)} — fluent builder; {@link Builder} IS a
     *       {@link MapSqlParameterSource} so no terminal call is needed.</li>
     * </ul>
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    final class Params {

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
}
