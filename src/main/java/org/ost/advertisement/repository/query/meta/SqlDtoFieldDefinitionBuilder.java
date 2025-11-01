package org.ost.advertisement.repository.query.meta;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlDtoFieldDefinitionBuilder {

	public static SqlDtoFieldDefinition<String> str(String dtoField, String sqlField) {
		return build(dtoField, sqlField, ResultSet::getString);
	}

	public static SqlDtoFieldDefinition<Long> id(String dtoField, String sqlField) {
		return build(dtoField, sqlField, (rs, alias) -> rs.getObject(alias, Long.class));
	}

	public static SqlDtoFieldDefinition<Long> longVal(String dtoField, String sqlField) {
		return build(dtoField, sqlField, (rs, alias) -> rs.getObject(alias, Long.class));
	}

	public static SqlDtoFieldDefinition<Boolean> bool(String dtoField, String sqlField) {
		return build(dtoField, sqlField, (rs, alias) -> rs.getObject(alias, Boolean.class));
	}

	public static SqlDtoFieldDefinition<Instant> instant(String dtoField, String sqlField) {
		return build(dtoField, sqlField, (rs, alias) -> {
			Timestamp ts = rs.getTimestamp(alias);
			return ts != null ? ts.toInstant() : null;
		});
	}

	public static <T> SqlDtoFieldDefinition<T> build(String dtoField, String sqlField, ValueExtractor<T> extractor) {
		return new SqlDtoFieldDefinition<>(dtoField, sqlField, extractor);
	}
}
