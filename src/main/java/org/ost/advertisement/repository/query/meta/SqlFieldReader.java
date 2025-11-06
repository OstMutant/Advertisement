package org.ost.advertisement.repository.query.meta;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlFieldReader<T> {

	T apply(ResultSet rs, String alias) throws SQLException;
}
