package org.ost.platform.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link SqlPredicate} refuses text and parameter names that could carry a value or a second statement, and wraps itself as an AND clause. */
class SqlPredicateTest {

    @Test
    void asAndClause_wrapsTheTextInParentheses() {
        SqlPredicate predicate = new SqlPredicate("x.id = :_a OR x.id = :_b", Map.of("_a", 1L, "_b", 2L));

        assertThat(predicate.asAndClause()).isEqualTo(" AND (x.id = :_a OR x.id = :_b)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"x.id = 1; DROP TABLE y", "x.id = 1 -- tail", "x.id = 1 /* c */"})
    void constructor_rejectsTextWithAStatementSeparatorOrComment(String sql) {
        assertThatThrownBy(() -> new SqlPredicate(sql, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "id", "Ab", "_1a", "_Ab", "_a-b", "_a b"})
    void constructor_rejectsAParameterNameWithoutAnUnderscoreAndLowerCaseStart(String name) {
        assertThatThrownBy(() -> new SqlPredicate("x.id = :" + name, Map.of(name, 1L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsAParameterThatTheTextDoesNotUse() {
        assertThatThrownBy(() -> new SqlPredicate("x.id = :_a", Map.of("_a", 1L, "_b", 2L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("_b");
    }

    @Test
    void constructor_rejectsAParameterNameThatOnlyPrefixesAnotherName() {
        assertThatThrownBy(() -> new SqlPredicate("x.id = :_taxonCityId", Map.of("_taxonCity", 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_acceptsAParameterUsedTwiceAndTheEmptyMap() {
        assertThat(new SqlPredicate("x.t = :_t AND y.t = :_t", Map.of("_t", "T")).params()).containsEntry("_t", "T");
        assertThat(new SqlPredicate("x.id IS NOT NULL", Map.of()).params()).isEmpty();
    }

    @Test
    void params_areACopyThatCannotBeChanged() {
        Map<String, Object> source = new HashMap<>(Map.of("_a", 1L));
        SqlPredicate predicate = new SqlPredicate("x.id = :_a", source);

        source.put("_b", 2L);

        assertThat(predicate.params()).containsOnlyKeys("_a");
        assertThatThrownBy(() -> predicate.params().put("_c", 3L)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_rejectsNull() {
        assertThatThrownBy(() -> new SqlPredicate(null, Map.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SqlPredicate("x.id IS NULL", null)).isInstanceOf(NullPointerException.class);
    }
}
