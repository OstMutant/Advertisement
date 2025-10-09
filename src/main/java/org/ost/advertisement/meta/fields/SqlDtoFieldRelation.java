package org.ost.advertisement.meta.fields;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface SqlDtoFieldRelation {

	String getSqlField();

	String getDtoField();

	<T> FieldDefinition.ValueExtractor<T> getExtractorLogic();

	default <T> T extract(ResultSet rs) throws SQLException {
		return this.<T>getExtractorLogic().apply(rs, getDtoField());
	}

	default <T> FieldDefinition<T> toFieldDefinition() {
		return new FieldDefinition<>(getDtoField(), getSqlField(), getExtractorLogic());
	}
}
