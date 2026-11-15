package org.ost.platform.core.model;

import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** A row restriction built by the module that owns the data, for another module to append to its own statement: the text and the named parameters it binds, never a value inside the text. */
public record SqlPredicate(@NonNull String sql, @NonNull Map<String, Object> params) {

    private static final Pattern PARAMETER_NAME = Pattern.compile("^_[a-z][A-Za-z0-9]*$");
    private static final List<String> FORBIDDEN_TOKENS = List.of(";", "--", "/*");

    public SqlPredicate {
        FORBIDDEN_TOKENS.stream().filter(sql::contains).findFirst().ifPresent(token -> {
            throw new IllegalArgumentException("SqlPredicate text contains " + token + ": " + sql);
        });
        params.keySet().forEach(name -> requireNamedInText(sql, name));
        params = Map.copyOf(params);
    }

    /** The text as an additional condition: {@code " AND (" + sql + ")"}, parenthesised so an {@code OR} inside cannot loosen the caller's own conditions. */
    public String asAndClause() {
        return " AND (" + sql + ")";
    }

    private static void requireNamedInText(String sql, String name) {
        if (!PARAMETER_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("SqlPredicate parameter name must start with an underscore and a lower-case letter: " + name);
        }
        if (!Pattern.compile(":" + Pattern.quote(name) + "(?![A-Za-z0-9_])").matcher(sql).find()) {
            throw new IllegalArgumentException("SqlPredicate parameter " + name + " does not occur in the text: " + sql);
        }
    }
}
