package org.ost.advertisement.repository.query.meta;

import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.Getter;

public record SqlDtoFieldDefinition<T>(
	@Getter String dtoField,
	@Getter String sqlField,
	@Getter ValueExtractor<T> extractor
) {

	public T extract(ResultSet rs) throws SQLException {
		return extractor.apply(rs, dtoField);
	}

}
