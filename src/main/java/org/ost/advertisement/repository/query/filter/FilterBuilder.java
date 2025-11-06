package org.ost.advertisement.repository.query.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public abstract class FilterBuilder<F> {

	protected final List<FilterBinding<F, ?>> relations = new ArrayList<>();

	public String build(MapSqlParameterSource params, F filter) {
		List<SqlCondition<?>> sqlConditions = new ArrayList<>();
		for (FilterBinding<F, ?> relation : relations) {
			SqlCondition<?> sqlCondition = relation.getCondition(filter);
			if(Objects.nonNull(sqlCondition)){
				sqlConditions.add(sqlCondition);
			}
		}
		toParams(sqlConditions).forEach(params::addValue);
		return toSql(sqlConditions);
	}

	private String toSql(List<SqlCondition<?>> sqlConditions) {
		return sqlConditions.stream()
			.map(SqlCondition::getConditionClause)
			.collect(Collectors.joining(" AND "));
	}

	public Map<String, Object> toParams(List<SqlCondition<?>> sqlConditions) {
		return sqlConditions.stream().collect(Collectors.toMap(SqlCondition::filterProperty, SqlCondition::value));
	}
}
