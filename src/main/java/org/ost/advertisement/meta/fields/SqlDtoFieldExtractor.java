package org.ost.advertisement.meta.fields;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface SqlDtoFieldExtractor<T> {

	T extract(ResultSet rs) throws SQLException;
}
