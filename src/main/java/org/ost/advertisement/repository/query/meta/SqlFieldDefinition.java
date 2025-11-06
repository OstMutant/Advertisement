package org.ost.advertisement.repository.query.meta;

import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.Getter;

public record SqlFieldDefinition<T>(
	@Getter String sqlExpression,
	@Getter String alias,
	@Getter SqlFieldReader<T> extractor
) {

	public T extract(ResultSet rs) throws SQLException {
		return extractor.apply(rs, alias);
	}

}
