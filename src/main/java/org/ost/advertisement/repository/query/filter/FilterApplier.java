package org.ost.advertisement.repository.query.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public abstract class FilterApplier<F> {

	protected final List<FilterRelation<F, ?>> relations = new ArrayList<>();

	public String apply(MapSqlParameterSource params, F filter) {
		List<Condition<?>> conditions = new ArrayList<>();
		for (FilterRelation<F, ?> relation : relations) {
			Condition<?> condition = relation.getCondition(filter);
			if(Objects.nonNull(condition)){
				conditions.add(condition);
			}
		}
		toParams(conditions).forEach(params::addValue);
		return toSql(conditions);
	}

	private String toSql(List<Condition<?>> conditions) {
		return conditions.stream()
			.map(Condition::getConditionClause)
			.collect(Collectors.joining(" AND "));
	}

	public Map<String, Object> toParams(List<Condition<?>> conditions) {
		return conditions.stream().collect(Collectors.toMap(Condition::filterProperty, Condition::value));
	}
}
