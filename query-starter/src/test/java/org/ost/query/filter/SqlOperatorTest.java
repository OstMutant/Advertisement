package org.ost.query.filter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlOperatorTest {

    @Test
    void equals_formatsClause() {
        assertThat(SqlOperator.EQUALS.formatClause("a.status", "status"))
                .isEqualTo("a.status = :status");
    }

    @Test
    void lessOrEqual_formatsClause() {
        assertThat(SqlOperator.LESS_OR_EQUAL.formatClause("a.created_at", "createdBefore"))
                .isEqualTo("a.created_at <= :createdBefore");
    }

    @Test
    void greaterOrEqual_formatsClause() {
        assertThat(SqlOperator.GREATER_OR_EQUAL.formatClause("a.created_at", "createdAfter"))
                .isEqualTo("a.created_at >= :createdAfter");
    }

    @Test
    void likeIgnoreCase_formatsClause() {
        assertThat(SqlOperator.LIKE_IGNORE_CASE.formatClause("a.title", "title"))
                .isEqualTo("a.title ILIKE :title");
    }

    @Test
    void in_formatsClause() {
        assertThat(SqlOperator.IN.formatClause("a.role", "roles"))
                .isEqualTo("a.role IN (:roles)");
    }
}
