package org.ost.query.filter;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlFilterBuilderTest {

    private static final SqlFilterMapping TITLE_MAPPING = new SqlFilterMapping() {
        @Override public String filterProperty() { return "title"; }
        @Override public String sqlExpression()  { return "a.title"; }
    };

    private static final SqlFilterMapping STATUS_MAPPING = new SqlFilterMapping() {
        @Override public String filterProperty() { return "status"; }
        @Override public String sqlExpression()  { return "a.status"; }
    };

    @Test
    void build_uniqueFilterProperties_joinsConditionsAndParams() {
        SqlFilterBuilder<String> builder = new SqlFilterBuilder<>(List.of(
                SqlBoundFilter.of(TITLE_MAPPING.filterProperty(), TITLE_MAPPING.sqlExpression(),
                        (m, v) -> SqlCondition.equalsTo(m, v)),
                SqlBoundFilter.of(STATUS_MAPPING.filterProperty(), STATUS_MAPPING.sqlExpression(),
                        (m, v) -> SqlCondition.equalsTo(m, v))
        ));
        MapSqlParameterSource params = new MapSqlParameterSource();

        String sql = builder.build(params, "irrelevant");

        assertThat(sql).isEqualTo("a.title = :title AND a.status = :status");
        assertThat(params.getValue("title")).isEqualTo("irrelevant");
        assertThat(params.getValue("status")).isEqualTo("irrelevant");
    }

    @Test
    void toParams_duplicateFilterPropertyBothPresent_throwsWithClearMessage() {
        SqlFilterBuilder<String> builder = new SqlFilterBuilder<>(List.of(
                SqlBoundFilter.of(TITLE_MAPPING.filterProperty(), TITLE_MAPPING.sqlExpression(),
                        (m, v) -> SqlCondition.equalsTo(m, v)),
                SqlBoundFilter.of(TITLE_MAPPING.filterProperty(), "a.other_title", (m, v) -> SqlCondition.equalsTo(m, v))
        ));

        assertThatThrownBy(() -> builder.build(new MapSqlParameterSource(), "irrelevant"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate filterProperty");
    }
}
