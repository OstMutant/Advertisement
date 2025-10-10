package org.ost.advertisement.meta.fields;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface ValueExtractor<T> {

	T apply(ResultSet rs, String dtoField) throws SQLException;
}
