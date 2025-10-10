package org.ost.advertisement.repository.query.meta;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface ValueExtractor<T> {

	T apply(ResultSet rs, String dtoField) throws SQLException;
}
