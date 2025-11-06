package org.ost.advertisement.repository.query.projection;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlFieldBuilder {

	public static SqlFieldDefinition<String> str(String sqlExpression, String sqlAlias) {
		return build(sqlExpression, sqlAlias, ResultSet::getString);
	}

	public static SqlFieldDefinition<Long> id(String sqlExpression, String sqlAlias) {
		return build(sqlExpression, sqlAlias, (rs, alias) -> rs.getObject(alias, Long.class));
	}

	public static SqlFieldDefinition<Long> longVal(String sqlExpression, String sqlAlias) {
		return build(sqlExpression, sqlAlias, (rs, alias) -> rs.getObject(alias, Long.class));
	}

	public static SqlFieldDefinition<Boolean> bool(String sqlExpression, String sqlAlias) {
		return build(sqlExpression, sqlAlias, (rs, alias) -> rs.getObject(alias, Boolean.class));
	}

	public static SqlFieldDefinition<Instant> instant(String sqlExpression, String sqlAlias) {
		return build(sqlExpression, sqlAlias, (rs, alias) -> {
			Timestamp ts = rs.getTimestamp(alias);
			return ts != null ? ts.toInstant() : null;
		});
	}

	public static <T> SqlFieldDefinition<T> build(String sqlExpression, String alias, SqlFieldReader<T> extractor) {
		return new SqlFieldDefinition<>(sqlExpression, alias, extractor);
	}
}
