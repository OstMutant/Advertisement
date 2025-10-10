package org.ost.advertisement.repository.query.exec;

import java.util.List;
import java.util.Optional;
import org.ost.advertisement.repository.query.mapping.FieldRelations;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class RepositoryExecutor<T> {

	private final NamedParameterJdbcTemplate jdbc;
	private final FieldRelations<T> mapper;

	public RepositoryExecutor(NamedParameterJdbcTemplate jdbc, FieldRelations<T> mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	public List<T> executeQuery(String sql, MapSqlParameterSource params) {
		return jdbc.query(sql, params, mapper);
	}

	public Optional<T> executeSingle(String sql, MapSqlParameterSource params) {
		return jdbc.query(sql, params, mapper).stream().findFirst();
	}

	public Long executeCount(String sql, MapSqlParameterSource params) {
		return jdbc.queryForObject(sql, params, Long.class);
	}
}
