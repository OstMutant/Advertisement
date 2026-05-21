package org.ost.sqlengine.common;

/**
 * A named SQL statement. Wraps a raw SQL string so call sites pass a typed constant
 * rather than an anonymous string literal.
 */
@FunctionalInterface
public interface SqlCommand {

    String sql();

    static SqlCommand of(String sql)                      { return () -> sql; }
    static SqlCommand of(String template, Object... args) { return of(sql(template, args)); }

    static String sql(String template, Object... args) {
        return SqlTemplateExpander.expand(template, args);
    }
}
