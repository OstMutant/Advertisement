package org.ost.advertisement.meta.fields;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface SqlDtoFieldRelation<T> {

	String getSqlField();

	String getDtoField();

	T extract(ResultSet rs) throws SQLException;
}
