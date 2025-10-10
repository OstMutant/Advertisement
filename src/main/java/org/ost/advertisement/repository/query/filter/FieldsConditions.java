package org.ost.advertisement.repository.query.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FieldsConditions {

	public record Condition(String sql, String param, Object value) {

	}

	private final List<Condition> conditions = new ArrayList<>();

	public FieldsConditions add(String sql, String param, Object value) {
		conditions.add(new Condition(sql, param, value));
		return this;
	}

	public String toSql(String joiner) {
		return conditions.stream()
			.map(Condition::sql)
			.collect(Collectors.joining(" " + joiner + " "));
	}

	public String toSqlApplyingAnd() {
		return toSql("AND");
	}

	public Map<String, Object> toParams() {
		return conditions.stream()
			.collect(Collectors.toMap(Condition::param, Condition::value, (a, b) -> b));
	}
}

