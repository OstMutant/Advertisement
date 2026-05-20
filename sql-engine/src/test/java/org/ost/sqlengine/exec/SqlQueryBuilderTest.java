package org.ost.sqlengine.exec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlQueryBuilderTest {

    private SqlQueryBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SqlQueryBuilder();
    }

    // ── select(fields, source) ────────────────────────────────────────────────

    @Test
    void select_withFields_buildsQuery() {
        assertThat(builder.select("a.id, a.title", "advertisement a"))
                .isEqualTo("SELECT a.id, a.title FROM advertisement a");
    }

    @Test
    void select_blankFields_usesWildcard() {
        assertThat(builder.select("", "advertisement a"))
                .isEqualTo("SELECT * FROM advertisement a");
    }

    // ── select(fields, source, where) ─────────────────────────────────────────

    @Test
    void select_withWhere_appendsWhereClause() {
        assertThat(builder.select("a.id", "advertisement a", "a.deleted_at IS NULL"))
                .isEqualTo("SELECT a.id FROM advertisement a WHERE a.deleted_at IS NULL");
    }

    @Test
    void select_blankWhere_omitsWhereKeyword() {
        assertThat(builder.select("a.id", "advertisement a", ""))
                .isEqualTo("SELECT a.id FROM advertisement a");
    }

    @Test
    void select_nullWhere_omitsWhereKeyword() {
        assertThat(builder.select("a.id", "advertisement a", null))
                .isEqualTo("SELECT a.id FROM advertisement a");
    }

    // ── select(fields, source, where, orderBy, limit) ─────────────────────────

    @Test
    void select_withOrderByAndLimit_buildsFullQuery() {
        assertThat(builder.select("a.id", "advertisement a", "a.deleted_at IS NULL",
                "ORDER BY a.created_at DESC", "LIMIT 10 OFFSET 0"))
                .isEqualTo("SELECT a.id FROM advertisement a WHERE a.deleted_at IS NULL ORDER BY a.created_at DESC LIMIT 10 OFFSET 0");
    }

    @Test
    void select_blankOrderByAndLimit_omitsThem() {
        assertThat(builder.select("a.id", "advertisement a", "a.deleted_at IS NULL", "", ""))
                .isEqualTo("SELECT a.id FROM advertisement a WHERE a.deleted_at IS NULL");
    }

    // ── count ─────────────────────────────────────────────────────────────────

    @Test
    void count_withWhere_buildsCountQuery() {
        assertThat(builder.count("advertisement a", "a.deleted_at IS NULL"))
                .isEqualTo("SELECT COUNT(*) FROM advertisement a WHERE a.deleted_at IS NULL");
    }

    @Test
    void count_blankWhere_omitsWhereClause() {
        assertThat(builder.count("advertisement a", ""))
                .isEqualTo("SELECT COUNT(*) FROM advertisement a");
    }
}
