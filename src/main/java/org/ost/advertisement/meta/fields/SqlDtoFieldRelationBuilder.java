package org.ost.advertisement.meta.fields;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlDtoFieldRelationBuilder {

	public static SqlDtoFieldRelation<String> str(String dtoField, String sqlField) {
		return custom(dtoField, sqlField, ResultSet::getString);
	}

	public static SqlDtoFieldRelation<Long> id(String dtoField, String sqlField) {
		return custom(dtoField, sqlField, (rs, alias) -> rs.getObject(alias, Long.class));
	}

	public static SqlDtoFieldRelation<Long> longVal(String dtoField, String sqlField) {
		return custom(dtoField, sqlField, (rs, alias) -> rs.getObject(alias, Long.class));
	}

	public static SqlDtoFieldRelation<Boolean> bool(String dtoField, String sqlField) {
		return custom(dtoField, sqlField, (rs, alias) -> rs.getObject(alias, Boolean.class));
	}

	public static SqlDtoFieldRelation<Instant> instant(String dtoField, String sqlField) {
		return custom(dtoField, sqlField, (rs, alias) -> {
			Timestamp ts = rs.getTimestamp(alias);
			return ts != null ? ts.toInstant() : null;
		});
	}

	public static <T> SqlDtoFieldRelation<T> custom(String dtoField, String sqlField,
													SqlDtoFieldDefinition.ValueExtractor<T> extractor) {
		return new SqlDtoFieldDefinition<>(dtoField, sqlField, extractor);
	}
}
