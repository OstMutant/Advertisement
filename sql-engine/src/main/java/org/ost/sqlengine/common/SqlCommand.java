package org.ost.sqlengine.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A named SQL statement. Wraps a raw SQL string so call sites pass a typed constant
 * rather than an anonymous string literal.
 */
@FunctionalInterface
public interface SqlCommand {

    String sql();

    Pattern PLACEHOLDER = Pattern.compile("\\{[^}]+}");

    static SqlCommand of(String sql)                      { return () -> sql; }
    static SqlCommand of(String template, Object... args) { return of(sql(template, args)); }

    static String sql(String template, Object... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "sql() requires even number of args (key-value pairs), got: " + args.length);
        }
        String result = template;
        for (int i = 0; i < args.length; i += 2) {
            String placeholder = "{" + args[i] + "}";
            String next = result.replace(placeholder, String.valueOf(args[i + 1]));
            if (next.equals(result)) {
                throw new IllegalArgumentException(
                        "sql() key not found in template: " + placeholder);
            }
            result = next;
        }
        Matcher m = PLACEHOLDER.matcher(result);
        if (m.find()) {
            throw new IllegalArgumentException("sql() unreplaced placeholder: " + m.group());
        }
        return result;
    }
}
