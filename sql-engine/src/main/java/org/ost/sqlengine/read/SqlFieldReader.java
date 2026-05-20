package org.ost.sqlengine.read;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reads a typed value from a {@link ResultSet} column identified by its alias.
 * Used as the extraction strategy in {@link SqlSelectField}.
 *
 * @param <T> the Java type to extract
 */
@FunctionalInterface
public interface SqlFieldReader<T> {

    T apply(ResultSet rs, String alias) throws SQLException;
}
