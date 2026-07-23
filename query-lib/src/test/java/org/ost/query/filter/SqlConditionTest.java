package org.ost.query.filter;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SqlConditionTest {

    private static final SqlFilterMapping MAPPING = new SqlFilterMapping() {
        @Override public String filterProperty() { return "title"; }
        @Override public String sqlExpression()  { return "a.title"; }
    };

    // ── like ─────────────────────────────────────────────────────────────────

    @Test
    void like_nonNull_wrapsValueWithPercent() {
        var condition = SqlCondition.like(MAPPING, "hello");
        assertThat(condition).isNotNull();
        assertThat(condition.value()).isEqualTo("%hello%");
        assertThat(condition.operator()).isEqualTo(SqlOperator.LIKE_IGNORE_CASE);
        assertThat(condition.getConditionClause()).isEqualTo("a.title ILIKE :title ESCAPE '\\'");
    }

    @Test
    void like_null_returnsNull() {
        assertThat(SqlCondition.like(MAPPING, null)).isNull();
    }

    @Test
    void like_percentInValue_isEscaped() {
        var condition = SqlCondition.like(MAPPING, "100%");
        assertThat(condition.value()).isEqualTo("%100\\%%");
    }

    @Test
    void like_underscoreInValue_isEscaped() {
        var condition = SqlCondition.like(MAPPING, "a_b");
        assertThat(condition.value()).isEqualTo("%a\\_b%");
    }

    @Test
    void like_backslashInValue_isEscaped() {
        var condition = SqlCondition.like(MAPPING, "a\\b");
        assertThat(condition.value()).isEqualTo("%a\\\\b%");
    }

    @Test
    void like_mixedMetacharacters_areAllEscaped() {
        var condition = SqlCondition.like(MAPPING, "50%_off\\now");
        assertThat(condition.value()).isEqualTo("%50\\%\\_off\\\\now%");
    }

    // ── equalsTo ──────────────────────────────────────────────────────────────

    @Test
    void equalsTo_nonNull_returnsCondition() {
        var condition = SqlCondition.equalsTo(MAPPING, "active");
        assertThat(condition).isNotNull();
        assertThat(condition.value()).isEqualTo("active");
        assertThat(condition.operator()).isEqualTo(SqlOperator.EQUALS);
    }

    @Test
    void equalsTo_null_returnsNull() {
        assertThat(SqlCondition.equalsTo(MAPPING, (String) null)).isNull();
    }

    @Test
    void equalsTo_long_nonNull_returnsCondition() {
        var condition = SqlCondition.equalsTo(MAPPING, 42L);
        assertThat(condition).isNotNull();
        assertThat(condition.value()).isEqualTo(42L);
        assertThat(condition.operator()).isEqualTo(SqlOperator.EQUALS);
    }

    @Test
    void equalsTo_long_null_returnsNull() {
        assertThat(SqlCondition.equalsTo(MAPPING, (Long) null)).isNull();
    }

    // ── after / before (Instant) ──────────────────────────────────────────────

    @Test
    void after_instant_nonNull_returnsCondition() {
        var now = Instant.now();
        var condition = SqlCondition.after(MAPPING, now);
        assertThat(condition).isNotNull();
        assertThat(condition.operator()).isEqualTo(SqlOperator.GREATER_OR_EQUAL);
        assertThat(condition.value().toInstant()).isEqualTo(now);
    }

    @Test
    void before_instant_null_returnsNull() {
        assertThat(SqlCondition.before(MAPPING, (Instant) null)).isNull();
    }

    // ── after / before (Long) ────────────────────────────────────────────────

    @Test
    void after_long_nonNull_returnsCondition() {
        var condition = SqlCondition.after(MAPPING, 100L);
        assertThat(condition).isNotNull();
        assertThat(condition.value()).isEqualTo(100L);
        assertThat(condition.operator()).isEqualTo(SqlOperator.GREATER_OR_EQUAL);
    }

    @Test
    void before_long_null_returnsNull() {
        assertThat(SqlCondition.before(MAPPING, (Long) null)).isNull();
    }

    // ── inSet ─────────────────────────────────────────────────────────────────

    enum Role { ADMIN, USER }

    @Test
    void inSet_nonEmpty_returnsConditionWithNames() {
        Set<Role> roles = Set.of(Role.ADMIN, Role.USER);
        var condition = SqlCondition.inSet(MAPPING, roles);
        assertThat(condition).isNotNull();
        assertThat(condition.operator()).isEqualTo(SqlOperator.IN);
        assertThat(condition.value()).containsExactlyInAnyOrder("ADMIN", "USER");
    }

    @Test
    void inSet_emptySet_returnsNull() {
        Set<Role> roles = Set.of();
        assertThat(SqlCondition.inSet(MAPPING, roles)).isNull();
    }

    @Test
    void inSet_nullSet_returnsNull() {
        Set<Role> roles = null;
        assertThat(SqlCondition.inSet(MAPPING, roles)).isNull();
    }

    // ── anyOf ─────────────────────────────────────────────────────────────────

    @Test
    void anyOf_nonEmpty_returnsConditionWithArrayValueAndAnyOfOperator() {
        var condition = SqlCondition.anyOf(MAPPING, Set.of(1L, 2L, 3L));
        assertThat(condition).isNotNull();
        assertThat(condition.operator()).isEqualTo(SqlOperator.ANY_OF);
        assertThat(condition.value()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(condition.getConditionClause()).isEqualTo("a.title = ANY(:title)");
    }

    @Test
    void anyOf_emptySet_returnsNull() {
        assertThat(SqlCondition.anyOf(MAPPING, Set.of())).isNull();
    }

    @Test
    void anyOf_nullSet_returnsNull() {
        assertThat(SqlCondition.anyOf(MAPPING, null)).isNull();
    }

    // ── getConditionClause ────────────────────────────────────────────────────

    @Test
    void getConditionClause_delegatesToOperator() {
        var condition = SqlCondition.of("a.title", "title", "test", SqlOperator.EQUALS);
        assertThat(condition.getConditionClause()).isEqualTo("a.title = :title");
    }
}
