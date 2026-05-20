package org.ost.sqlengine.common;

/**
 * A named SQL statement. Wraps a raw SQL string so call sites pass a typed constant
 * rather than an anonymous string literal.
 */
@FunctionalInterface
public interface SqlCommand {

    String sql();

    static SqlCommand of(String sql) { return () -> sql; }
}
