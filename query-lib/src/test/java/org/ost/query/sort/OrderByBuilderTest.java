package org.ost.query.sort;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers {@link OrderByBuilder#build}, including tiebreaker recursion and the empty-sort default fallback. */
class OrderByBuilderTest {

    // ── build(sort, List<SortField>) — stable tiebreakers ───────────────────────

    private static final List<SortField> FIELDS = List.of(
            SortField.of("id",    "a.id"),
            SortField.of("title", "a.title"),
            SortField.of("createdAt", "a.created_at", SortField.of("id", "a.id")));

    @Test
    void buildFields_emptySort_returnsEmptyString() {
        assertThat(OrderByBuilder.build(Sort.unsorted(), FIELDS)).isEmpty();
    }

    @Test
    void buildFields_fieldWithoutTiebreaker_buildsPlainClause() {
        Sort sort = Sort.by(Sort.Direction.ASC, "title");
        assertThat(OrderByBuilder.build(sort, FIELDS)).isEqualTo(" ORDER BY a.title ASC NULLS LAST");
    }

    @Test
    void buildFields_fieldWithTiebreaker_appendsTiebreakerWithDefaultDescending() {
        Sort sort = Sort.by(Sort.Direction.ASC, "createdAt");
        assertThat(OrderByBuilder.build(sort, FIELDS))
                .isEqualTo(" ORDER BY a.created_at ASC NULLS LAST, a.id DESC NULLS LAST");
    }

    @Test
    void buildFields_descendingPrimarySort_tiebreakerStaysDescendingByDefault() {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        assertThat(OrderByBuilder.build(sort, FIELDS))
                .isEqualTo(" ORDER BY a.created_at DESC NULLS LAST, a.id DESC NULLS LAST");
    }

    @Test
    void buildFields_tiebreakerPropertyAlreadyInSort_notDuplicated() {
        Sort sort = Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        assertThat(OrderByBuilder.build(sort, FIELDS))
                .isEqualTo(" ORDER BY a.created_at ASC NULLS LAST, a.id DESC NULLS LAST");
    }

    @Test
    void buildFields_unknownProperty_isSkipped() {
        Sort sort = Sort.by(Sort.Direction.ASC, "unknownField");
        assertThat(OrderByBuilder.build(sort, FIELDS)).isEmpty();
    }

    @Test
    void buildFields_explicitTiebreakerDirection_overridesDefault() {
        List<SortField> fieldsWithAscTiebreaker = List.of(
                SortField.of("createdAt", "a.created_at", SortField.of("id", "a.id", Sort.Direction.ASC)));
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        assertThat(OrderByBuilder.build(sort, fieldsWithAscTiebreaker))
                .isEqualTo(" ORDER BY a.created_at DESC NULLS LAST, a.id ASC NULLS LAST");
    }

    @Test
    void buildFields_nestedTiebreaker_isAppendedAfterItsParent() {
        List<SortField> nested = List.of(
                SortField.of("createdAt", "a.created_at",
                        SortField.of("updatedAt", "a.updated_at", SortField.of("id", "a.id"))));
        Sort sort = Sort.by(Sort.Direction.ASC, "createdAt");
        assertThat(OrderByBuilder.build(sort, nested))
                .isEqualTo(" ORDER BY a.created_at ASC NULLS LAST, a.updated_at DESC NULLS LAST, a.id DESC NULLS LAST");
    }

    @Test
    void buildFields_tiebreakerPropertyAlsoExplicitlyRequestedLaterInSort_usesItsOwnRequestedDirection() {
        List<SortField> fields = List.of(
                SortField.of("createdAt", "a.created_at", SortField.of("id", "a.id")),
                SortField.of("id", "a.id"));
        Sort sort = Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
        assertThat(OrderByBuilder.build(sort, fields))
                .isEqualTo(" ORDER BY a.created_at ASC NULLS LAST, a.id ASC NULLS LAST");
    }

    @Test
    void buildFields_twoPrimaryFieldsSharingATiebreaker_appendsItOnlyOnce() {
        List<SortField> fields = List.of(
                SortField.of("createdAt", "a.created_at", SortField.of("id", "a.id")),
                SortField.of("updatedAt", "a.updated_at", SortField.of("id", "a.id")));
        Sort sort = Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.DESC, "updatedAt"));
        assertThat(OrderByBuilder.build(sort, fields))
                .isEqualTo(" ORDER BY a.created_at ASC NULLS LAST, a.id DESC NULLS LAST, a.updated_at DESC NULLS LAST");
    }

    // ── build(sort, List<SortField>) — empty-sort default fallback ──────────────

    @Test
    void buildFields_emptySortNoFieldHasDirection_returnsEmptyString() {
        // None of FIELDS' entries declare their own direction -- matches all 4 other repos' behavior.
        assertThat(OrderByBuilder.build(Sort.unsorted(), FIELDS)).isEmpty();
    }

    @Test
    void buildFields_emptySortOneFieldHasExplicitDirection_fallsBackToIt() {
        List<SortField> withDefault = List.of(
                SortField.of("createdAt", "a.created_at", Sort.Direction.DESC, SortField.of("id", "a.id")));
        assertThat(OrderByBuilder.build(Sort.unsorted(), withDefault))
                .isEqualTo(" ORDER BY a.created_at DESC NULLS LAST, a.id DESC NULLS LAST");
    }

    @Test
    void buildFields_emptySortAndEmptyFieldList_returnsEmptyString() {
        assertThat(OrderByBuilder.build(Sort.unsorted(), List.<SortField>of())).isEmpty();
    }

    @Test
    void buildFields_emptySortDefaultFieldPrecededByFieldWithoutDirection_stillFallsBackToIt() {
        List<SortField> fields = List.of(
                SortField.of("id", "a.id"),
                SortField.of("createdAt", "a.created_at", Sort.Direction.DESC, SortField.of("id", "a.id")));
        assertThat(OrderByBuilder.build(Sort.unsorted(), fields))
                .isEqualTo(" ORDER BY a.created_at DESC NULLS LAST, a.id DESC NULLS LAST");
    }
}
