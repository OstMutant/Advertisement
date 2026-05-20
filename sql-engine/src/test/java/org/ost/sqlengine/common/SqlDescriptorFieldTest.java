package org.ost.sqlengine.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlDescriptorFieldTest {

    @Test
    void columnName_simpleExpression_returnsExpression() {
        var field = field("title", "title");
        assertThat(field.columnName()).isEqualTo("title");
    }

    @Test
    void columnName_withTableAlias_returnsColumnPart() {
        var field = field("a.title", "title");
        assertThat(field.columnName()).isEqualTo("title");
    }

    @Test
    void columnName_withMultipleDots_returnsAfterFirstDot() {
        var field = field("schema.table.column", "col");
        assertThat(field.columnName()).isEqualTo("table.column");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOWER(a.email)", "COALESCE(a.name, '')", "a.title || ' ' || a.body"})
    void columnName_complexExpression_throws(String expr) {
        var field = field(expr, "alias");
        assertThatThrownBy(field::columnName)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining(expr);
    }

    @Test
    void constructor_blankSqlExpression_throws() {
        assertThatThrownBy(() -> field("  ", "alias"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SQL expression");
    }

    @Test
    void constructor_blankAlias_throws() {
        assertThatThrownBy(() -> field("a.title", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alias");
    }

    @Test
    void extract_delegatesToExtractor() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("title")).thenReturn("Hello");

        var field = SqlDescriptorField.<String>builder()
                .sqlExpression("a.title")
                .alias("title")
                .extractor(ResultSet::getString)
                .build();

        assertThat(field.extract(rs)).isEqualTo("Hello");
    }

    private static SqlDescriptorField<String> field(String expr, String alias) {
        return SqlDescriptorField.<String>builder()
                .sqlExpression(expr)
                .alias(alias)
                .extractor(ResultSet::getString)
                .build();
    }
}
