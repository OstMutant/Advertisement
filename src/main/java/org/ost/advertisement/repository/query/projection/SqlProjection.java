package org.ost.advertisement.repository.query.projection;

import static java.util.Optional.ofNullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;

public abstract class SqlProjection<T> implements RowMapper<T> {

	private final Map<String, String> aliasToSqlMap;
	@Getter
	private final String sqlSource;

	protected SqlProjection(List<SqlFieldDefinition<?>> items, String sqlSource) {
		Objects.requireNonNull(items, "Parameter 'items' must not be null.");
		Objects.requireNonNull(sqlSource, "Parameter 'sqlSource' must not be null.");
		this.aliasToSqlMap = items.stream()
			.collect(Collectors.toMap(
				SqlFieldDefinition::alias,
				SqlFieldDefinition::sqlExpression,
				(existing, replacement) -> existing,
				HashMap::new
			));
		this.sqlSource = sqlSource;
	}

	public String getSelectClause() {
		return aliasToSqlMap.entrySet().stream()
			.map(e -> e.getValue() + " AS " + e.getKey())
			.collect(Collectors.joining(", "));
	}

	public String getOrderByClause(Sort sort) {
		String orderByFragment = ofNullable(sort)
			.filter(s -> !s.isEmpty())
			.map(Sort::stream)
			.orElseGet(Stream::empty)
			.map(order -> ofNullable(aliasToSqlMap.get(order.getProperty()))
				.map(col -> col + " " + order.getDirection().name())
				.orElse(null))
			.filter(Objects::nonNull)
			.collect(Collectors.joining(", "));
		return StringUtils.isBlank(orderByFragment) ? "" : "ORDER BY " + orderByFragment;
	}
}
