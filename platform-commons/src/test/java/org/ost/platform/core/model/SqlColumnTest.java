package org.ost.platform.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link SqlColumn} accepts only lower-case identifiers and derives the qualified column text. */
class SqlColumnTest {

    @Test
    void sql_joinsAliasAndColumn() {
        assertThat(new SqlColumn("pp", "actor_id").sql()).isEqualTo("pp.actor_id");
        assertThat(new SqlColumn("a", "id").sql()).isEqualTo("a.id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "_ta", "a.id", "a id", "a;", "\"a\"", "a--", "1a", "", "a-b"})
    void constructor_rejectsAnAliasThatIsNotALowerCaseIdentifier(String alias) {
        assertThatThrownBy(() -> new SqlColumn(alias, "id")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("alias");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Id", "id; DROP TABLE x", "a.b", "id)", "", "x/*"})
    void constructor_rejectsAColumnThatIsNotALowerCaseIdentifier(String column) {
        assertThatThrownBy(() -> new SqlColumn("a", column)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("column");
    }

    @Test
    void constructor_rejectsAPartLongerThan63Characters() {
        assertThat(new SqlColumn("a".repeat(63), "id").alias()).hasSize(63);
        assertThatThrownBy(() -> new SqlColumn("a".repeat(64), "id")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsNull() {
        assertThatThrownBy(() -> new SqlColumn(null, "id")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SqlColumn("a", null)).isInstanceOf(NullPointerException.class);
    }
}
