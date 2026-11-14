package org.ost.platform.core.model;

import lombok.NonNull;

import java.util.regex.Pattern;

/** A consumer's own table alias and key column that a provider-built {@link SqlPredicate} restricts; both parts are lower-case identifiers. */
public record SqlColumn(@NonNull String alias, @NonNull String column) {

    private static final Pattern IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    public SqlColumn {
        requireIdentifier("alias", alias);
        requireIdentifier("column", column);
    }

    /** The qualified column as it is written in SQL: {@code alias.column}. */
    public String sql() {
        return alias + "." + column;
    }

    private static void requireIdentifier(String part, String value) {
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("SqlColumn " + part + " is not a lower-case identifier: " + value);
        }
    }
}
