package org.ost.advertisement.meta.fields;

import java.sql.ResultSet;
import java.sql.SQLException;

public record FieldDefinition<T>(
	String dtoField,
	String sqlField,
	ValueExtractor<T> extractor
) {

	@FunctionalInterface
	public interface ValueExtractor<T> {

		T apply(ResultSet rs, String dtoField) throws SQLException;
	}
}
