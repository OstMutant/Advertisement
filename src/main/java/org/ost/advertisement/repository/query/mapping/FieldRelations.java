package org.ost.advertisement.repository.query.mapping;

import static java.util.Optional.ofNullable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.ost.advertisement.meta.fields.SqlDtoFieldDefinition;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;

public abstract class FieldRelations<T> implements RowMapper<T> {

	private final Map<String, String> dtoToSqlRelations;
	private final String sqlSource;

	protected FieldRelations(SqlDtoFieldDefinition<?>[] items, String sqlSource) {
		this.dtoToSqlRelations = Stream.of(items)
			.collect(Collectors.toMap(
				SqlDtoFieldDefinition::dtoField,
				SqlDtoFieldDefinition::sqlField,
				(existing, replacement) -> existing,
				HashMap::new
			));
		this.sqlSource = sqlSource;
	}

	public String sourceToSql() {
		return sqlSource;
	}

	public String fieldsToSql() {
		return dtoToSqlRelations.entrySet().stream()
			.map(e -> e.getValue() + " AS " + e.getKey())
			.collect(Collectors.joining(", "));
	}

	public String sortToSql(Sort sort) {
		String orderByFragment = ofNullable(sort)
			.filter(s -> !s.isEmpty())
			.map(Sort::stream)
			.orElseGet(Stream::empty)
			.map(order -> ofNullable(dtoToSqlRelations.get(order.getProperty()))
				.map(col -> col + " " + order.getDirection().name())
				.orElse(null))
			.filter(Objects::nonNull)
			.collect(Collectors.joining(", "));
		return StringUtils.isBlank(orderByFragment) ? "" : " ORDER BY " + orderByFragment;
	}

	public static Timestamp toTimestamp(Instant instant) {
		return instant != null ? Timestamp.from(instant) : null;
	}

	public static Instant toInstant(Timestamp ts) {
		return ts != null ? ts.toInstant() : null;
	}
}
