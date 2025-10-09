package org.ost.advertisement.meta.fields;

import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.Getter;

public record SqlDtoFieldDefinition<T>(
	@Getter String dtoField,
	@Getter String sqlField,
	@Getter ValueExtractor<T> extractor
) implements SqlDtoFieldRelation<T> {

	@Override
	public T extract(ResultSet rs) throws SQLException {
		return extractor.apply(rs, dtoField);
	}

	@FunctionalInterface
	public interface ValueExtractor<T> {

		T apply(ResultSet rs, String dtoField) throws SQLException;
	}
}
