package org.ost.advertisement.repository.query.projection;

import java.sql.ResultSet;
import java.sql.SQLException;

public record SqlFieldDefinition<T>(
	String sqlExpression,
	String alias,
	SqlFieldReader<T> extractor
) implements SqlFieldProjection {

	public T extract(ResultSet rs) throws SQLException {
		return extractor.apply(rs, alias);
	}

}
