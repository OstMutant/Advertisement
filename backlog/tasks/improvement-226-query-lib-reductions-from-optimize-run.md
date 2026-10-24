# improvement-226: query-lib reductions from the /optimize run

**Type:** improvement — follow-up of the first full `/optimize path query-lib` run (deep plan `optimize-query-lib`, 2026-10-24).
**Module:** `query-lib/` (`SqlCondition`, `SortField`, `SqlFilterBuilder`, three test files, `README.md`, `pom.xml`), `.claude/rules/query-lib.md`.
**Priority:** 🟡 Top — first in the backlog; done after the optimizer run, small and measured, so it can be completed right away.
**When:** independent; follows [improvement-225](improvement-225-code-reduction-findings.md) (the findings inventory, entry OPT-1 holds the README and rule-file part already applied).

## Current state

The first `/optimize path query-lib` run found one reduction in the review (stale README and rule-file text) and, in the deep plan that followed, a further set of reductions in the code and tests: F1-F6 and D3 in the execution plan below. Done on 2026-10-24: D1 variant A (stale README lines corrected) and D2 (rule file), recorded as OPT-1 in the inventory (net -14 lines in 2 files). Not done: F1-F6, D3, and the six decisions listed at the end of the plan for people.

## Why change

`query-lib/` carries a private copy of a constructor, a second `SortField.of` the varargs version already covers, two public methods nobody outside the class calls, a test that is an exact copy of another (with a wrong count in its comment), two throw-away mapping objects in a test, and one wrong dependency comment in `pom.xml`.

## Expected benefit

About -56 lines of code and tests (estimate, not measured; `check-batch.sh` writes the actual figure) with every caller unchanged and every behavior still covered by a test.

## Approach

Apply the execution plan below in small batches after the user answers the decisions: haiku for the steps with exact edits, sonnet for the README rewrite (variant B, if chosen), the smallest tests first (`build-and-test.sh --unit-test query-lib --skip-vaadin`), then the Definition of Done suite. The API-scope ADR of this module (no DSL extensions) forbids adding to the API, not removing unused methods; the plan's decision 1 still asks the user. Behavior of `OrderByBuilder` with a `SortField` that declares a direction (decision 4) is a possible bug, proposed for the deferred-findings task, not changed here.

## Related

[improvement-225](improvement-225-code-reduction-findings.md) (inventory), [improvement-224](improvement-224-planning-and-optimization-commands.md) (the `/optimize` and `/deep-plan` commands), the module's own `DECISIONS.md` (API scope frozen).

## Plan for people



### Goal

Make `query-lib/` smaller to read, test and keep up to date, without losing readability, type safety,
test coverage or the architecture, and without changing what the library does.

### The problem this solves

The library itself is already small (10 classes, about 380 lines of Java, 4 test files). What it carries
that it does not need:

- **Duplicates in the code.** A private copy of a constructor (`SqlCondition.of`), a second
  `SortField.of` that the varargs version already covers, a public `SqlFilterBuilder.build` overload
  nobody outside the class calls, and an `equalsTo(..., Long)` that no repository uses.
- **Duplicates in the tests.** One `OrderByBuilderTest` case is an exact copy of another (and carries a
  wrong count in its comment); `SqlFilterBuilderTest` builds two throw-away mapping objects only to read
  two strings back out of them.
- **Documentation that describes code which no longer exists.** `README.md` (and the module's rule file
  `.claude/rules/query-lib.md`) explain an `OrderByBuilder.build(sort, Map)` method that was removed when
  stable tiebreakers came in; every repository now uses the `SortField` list. The README also calls
  `SqlFilterBinding` a functional interface (it is not) and the `pom.xml` comment names the wrong
  sources for `Sort`/`Pageable`.

Two automatic reviews ran before this plan. The first read only the 10 main classes and found nothing.
The second read all 18 files and found one thing: the stale README and rule-file text (D1 and D2 below).
This plan adds what came from checking every caller in the 6 repositories that use the library (F1-F4)
and from the tests (F5, F6). The second review rejected "tidying the test helpers" as a few lines at most;
this plan keeps F5 and F6 because F5 removes an exact duplicate with a wrong comment and F6 fixes a false
test description, and leaves the choice to you (decision 6). The second review also wondered whether
`OffsetPageable` is unused: it is used by `UserService` in the user starter, so it stays.

### The means needed

- Edits in 7 files inside `query-lib/` (3 main classes, 3 tests, README), plus one comment in its
  `pom.xml`. No other module's code changes: every caller compiles as it is.
- Optionally, one edit in `.claude/rules/query-lib.md` (outside `query-lib/`, so only if you say so).
- The usual checks: the project build with unit tests, and `check-batch.sh` from `/optimize` to measure
  the saving.

### What the result will look like

| ID | What | Lines (est.) | Risk |
|---|---|---|---|
| F5 | Delete the duplicated `OrderByBuilderTest` case | -6 test | none |
| F6 | Inline the two throw-away mappings in `SqlFilterBuilderTest`, fix its description | -13 test | none |
| F1 | Use the record constructor instead of the private `SqlCondition.of`; drop the test that only covered it (its check is also in `SqlOperatorTest`) | -4 main, -8 test | none |
| F2 | Remove the unused `SqlCondition.equalsTo(..., Long)` and its 2 tests | -4 main, -13 test | removes a public method (decision 1) |
| F3 | Remove `SortField.of(property, expression)`; the varargs `of` takes the same calls unchanged | -4 main | removes a public method (decision 1) |
| F4 | Fold the 2-argument `SqlFilterBuilder.build` into the 3-argument one; field becomes `private` | -4 main | removes a public method (decision 1) |
| D1 | README: fix the 6 stale statements (variant A) or rewrite to the project's README standard (variant B) | A about -7, B about -60 | none (decision 2) |
| D2 | Rule file: remove the description of the removed `Map` method, fix the empty-sort sentence | about -15 | outside `query-lib/` (decision 3) |
| D3 | `pom.xml`: correct one dependency comment | 0 | none |

Code and tests together: about -56 lines, all callers unchanged, every behavior still covered by a test
(F6 even makes the remaining test cover the prefix, which it does not today).

### Cost

| Part | Executor | Effort | Tokens (estimate) |
|---|---|---|---|
| F1-F6 (4 steps, exact edits written in the machine plan) | haiku | about 15 minutes | about 60-100k in total |
| D1 variant A, D3 (exact edits) | haiku | about 5 minutes | about 20-30k |
| D1 variant B (rewrite to the standard) | sonnet | about 15 minutes | about 60-100k |
| D2 (rule file) | sonnet | about 10 minutes | about 30-50k |
| Review of the diff and the build run | main thread, no model for the build | 7-10 minutes of build time | review only |

These are estimates, not measurements.

### Risks

- **Binary compatibility (F2-F4).** Removing a public method breaks a consumer compiled against the old
  jar. All consumers are in this same build and none calls these methods (checked with `git grep`), so
  the risk exists only for code outside this repository.
- **Overload choice (F3).** After removal, `SortField.of("id", "a.id")` resolves to the varargs `of` with
  no tiebreakers, which builds exactly the same value. Confirmed by the Java language rules (JLS 15.12.2);
  the build proves it.
- **Docs drifting again.** Variant A keeps the per-class list in the README, which can go stale again
  the same way; variant B removes that list.

### Where the project's practice differs from best practice

1. **The library depends on an application starter.** `pom.xml` declares `spring-boot-starter-data-jdbc`
   only to get `Sort` and `Pageable`, which live in `spring-data-commons` (checked in the jar). The starter
   also brings a connection pool, Spring Boot autoconfiguration and Spring Data JDBC, none of which this
   library uses. Maven's own guide recommends declaring exactly the dependencies the code uses
   (`spring-data-commons` and `spring-jdbc`). Doing so adds a `<dependency>` line, which the brief forbids
   and `check-batch.sh` flags, so it is **not** in this plan. Cost if done later: one small pom change and
   a full build; nothing else in the project changes.
2. **Two project rules pull in different directions for the README.** The README standard says a README
   must not restate what a class's own Javadoc already says, while `.claude/rules/query-lib.md` names the
   README's factory-method table as the place for that list. Variant B follows the README standard and
   moves the rule file's pointer; variant A keeps both as they are.
3. **Defensive null checks.** `OrderByBuilder` (`sort != null`) and `PaginationSqlBuilder`
   (`pageable == null`) guard against null, which the project's "no defensive empty checks" rule
   discourages. Removing them changes behavior (an exception instead of an empty clause), so the plan
   leaves them.

### Decisions waiting for you

1. **Remove the unused public methods (F2, F3, F4)?** They have no caller outside `query-lib/`. The brief
   forbids changing contracts; these are library methods nobody uses. Yes means three more steps; no
   leaves them and the plan still does F1, F5, F6, D1, D3. Note: F3 goes against an earlier "rejected" line
   in the findings inventory, whose reason ("used by every repository") does not hold for this one
   overload, because the varargs version takes the same calls.
2. **README: variant A or B?** A fixes the 6 wrong statements only. B rewrites the README to the
   project's README standard (purpose, how a repository uses the classes together, dependencies) and
   drops the per-class list and the factory table that repeat the Javadoc.
3. **Fix `.claude/rules/query-lib.md` too (D2)?** It is outside the `/optimize path query-lib` scope, but
   it repeats the same removed `Map` method and says direction defaults apply only to an empty sort.
4. **Is `OrderByBuilder`'s current behavior intended?** A `SortField` with a declared direction is added
   even when the caller asked for another sort. For the moderation queue sorted by type this gives
   `ORDER BY item_type, id DESC, created_at DESC, item_type DESC` (traced by reading the code, not run):
   `id` comes before `created_at`, and `item_type` appears twice. Changing it is a behavior change, out of
   this plan. Proposal: add it, and the stale line in `integration-tests/README.md` that mentions the
   removed sort map, to the standing deferred-findings task.
5. **Replace the starter dependency (difference 1)?** Only as separate work, outside `/optimize`.
6. **Keep the test tidy-up (F5, F6)?** The second review rejected it as too small; this plan proposes it
   for the reasons above. Saving: about 19 test lines.

## Execution plan


(exact content for a weaker model, exact files and behavior for a stronger one).

### Before any step

- Every step is part of an `/optimize apply` batch and runs only after the person approved it in the
  Approval Rule format of `.claude/rules.md`. Never commit. Run `git add` on each changed file.
- Steps 2, 3 and 4 run only if the person answered **yes to decision 1** (remove unused public methods).
  Step 6a or 6b runs according to **decision 2**. Step 7 runs only on **yes to decision 3**. Steps 1, 5, 8
  and 9 need no decision. Decision 6 (test tidy-up) gates steps 1 and 5.
- Do not touch any file outside the ones a step names. No `<dependency>` change, no reflection, nothing
  under `marketplace-orchestrator/`.
- A Haiku executor must write exactly what the step says. If the "old text" of a step is not found
  verbatim, stop and report; do not improvise.

### Inventory entries (for the main thread, in the `/optimize` entry format)

| OPT | Title | Kind | Where | Estimate | Recommendation |
|---|---|---|---|---|---|
| F5 | Duplicate empty-sort test | test-setup | `OrderByBuilderTest.java:99-104` | -6 +0, 1 file | implement |
| F6 | Throw-away mappings in SqlFilterBuilderTest | test-setup | `SqlFilterBuilderTest.java:11-52` | -13 +0, 1 file | implement |
| F1 | Private `SqlCondition.of` duplicates the canonical constructor | forwarding | `SqlCondition.java:27-30`, `SqlConditionTest.java:162-169` | -12 +0, 2 files | implement |
| F2 | Unused `equalsTo(SqlFilterMapping, Long)` | duplication | `SqlCondition.java:47-50`, `SqlConditionTest.java:73-85` | -17 +0, 2 files | investigate (decision 1) |
| F3 | `SortField.of(String,String)` covered by varargs `of` | duplication | `SortField.java:12-15` | -4 +0, 1 file | investigate (decision 1) |
| F4 | 2-arg `SqlFilterBuilder.build` has no external caller | forwarding | `SqlFilterBuilder.java:21-39` | -4 +0 main; test lines change, not count | investigate (decision 1) |
| D1 | README describes removed `OrderByBuilder.build(Sort, Map)` and other stale facts | convention | `query-lib/README.md:14,20-21,51,78-96,113` | A: about -7; B: about -60 | implement (variant by decision 2) |
| D2 | Rule file describes the removed alias-map overload | convention | `.claude/rules/query-lib.md:17-19,36-56,60-62,77-80` | about -15 | investigate (decision 3) |
| D3 | Wrong pom dependency comment | convention | `query-lib/pom.xml:26` | 0 | implement |

Estimates are physical lines counted from the current files, not measured; `check-batch.sh` writes the
actual numbers.

### Steps

#### Step 1 -- delete the duplicated OrderByBuilder test (F5)

- **Depends on:** decision 6 = yes.
- **Files:** `query-lib/src/test/java/org/ost/query/sort/OrderByBuilderTest.java`
- **Executor:** haiku
- **Input:** the current file; lines 20-23 and 99-103 hold the same call and assertion.
- **Exact change:** replace this old text (including the blank line after it)

```java
    @Test
    void buildFields_emptySortNoFieldHasDirection_returnsEmptyString() {
        // None of FIELDS' entries declare their own direction -- matches all 4 other repos' behavior.
        assertThat(OrderByBuilder.build(Sort.unsorted(), FIELDS)).isEmpty();
    }

```

  with nothing. The test `buildFields_emptySort_returnsEmptyString` at the top of the file stays.
- **Verification:** `git grep -c "OrderByBuilder.build(Sort.unsorted(), FIELDS)" query-lib` prints 1 for
  this file; the file still compiles in step 9.
- **Sources:** none (repository fact).

#### Step 2 -- SqlCondition: constructor instead of `of`, drop unused `equalsTo(Long)` (F1, F2)

- **Depends on:** F1 needs nothing; F2 needs decision 1 = yes. If decision 1 = no, apply only the F1 parts
  (keep the `equalsTo(..., Long)` method and its two tests).
- **Files:** `query-lib/src/main/java/org/ost/query/filter/SqlCondition.java`,
  `query-lib/src/test/java/org/ost/query/filter/SqlConditionTest.java`
- **Executor:** haiku
- **Input:** the current files.
- **Exact change in `SqlCondition.java`:**
  1. Delete (with the blank line after it):

```java
    static <R> SqlCondition<R> of(String sqlExpression, String filterProperty, R value, SqlOperator operator) {
        return new SqlCondition<>(sqlExpression, filterProperty, value, operator);
    }

```

  2. (F2) Delete (with the blank line after it):

```java
    public static SqlCondition<Long> equalsTo(SqlFilterMapping filterMapping, Long value) {
        return applyIfPresent(filterMapping, value, SqlOperator.EQUALS, Function.identity());
    }

```

  3. In both `applyIfPresent` and `applyIfNotEmpty`, replace the text `? SqlCondition.of(filterMapping.sqlExpression(),`
     with `? new SqlCondition<>(filterMapping.sqlExpression(),`. Nothing else changes; the next line
     (`valueMapper.apply(value), operator)`) stays as it is.
- **Exact change in `SqlConditionTest.java`:**
  1. (F2) Delete (with the blank line after it):

```java
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

```

  2. (F1) Delete the last section, from the blank line before it to the end of the test method, so that the
     file ends with the `anyOf_nullSet_returnsNull` test followed by the class's closing `}`:

```java

    // ── getConditionClause ────────────────────────────────────────────────────

    @Test
    void getConditionClause_delegatesToOperator() {
        var condition = SqlCondition.of("a.title", "title", "test", SqlOperator.EQUALS);
        assertThat(condition.getConditionClause()).isEqualTo("a.title = :title");
    }
```

- **Verification:** `git grep -n "SqlCondition.of(" -- '*.java'` finds nothing;
  `git grep -n "equalsTo(SqlFilterMapping filterMapping, Long" query-lib` finds nothing (F2 only); the
  operator-to-clause check stays covered by `SqlOperatorTest.equals_formatsClause` and by the clause
  assertions in `like_nonNull_wrapsValueWithPercent` and `anyOf_nonEmpty_...`. Build in step 9.
- **Sources:** https://martinfowler.com/bliki/Yagni.html

#### Step 3 -- SortField: drop the fixed-arity `of(property, expression)` (F3)

- **Depends on:** decision 1 = yes.
- **Files:** `query-lib/src/main/java/org/ost/query/sort/SortField.java`
- **Executor:** haiku
- **Input:** the current file.
- **Exact change:** delete (with the blank line after it):

```java
    public static SortField of(String property, String expression) {
        return new SortField(property, expression, null, List.of());
    }

```

  The overload `of(String property, String expression, SortField... tiebreakers)` stays and now takes
  every two-argument call. No call site changes.
- **Verification:** the reactor build in step 9 compiles all 6 repositories that call `SortField.of`
  (Advertisement, AuditLog, Feedback, ProviderProfile, Taxon, User) and `OrderByBuilderTest` passes
  unchanged.
- **Sources:** https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html (15.12.2: varargs overloads
  are tried only in phase 3)

#### Step 4 -- SqlFilterBuilder: one `build` method, private field; tests updated (F4, F6)

- **Depends on:** F4 needs decision 1 = yes; F6 needs decision 6 = yes. If only one of them is approved,
  apply only its part (F6 alone: keep the 2-arg calls in the test; F4 alone: keep the mappings but add the
  prefix argument as below).
- **Files:** `query-lib/src/main/java/org/ost/query/filter/SqlFilterBuilder.java`,
  `query-lib/src/test/java/org/ost/query/filter/SqlFilterBuilderTest.java`
- **Executor:** haiku
- **Input:** the current files.
- **Exact change in `SqlFilterBuilder.java` (F4):** replace the old text

```java
    protected final List<SqlFilterBinding<F, ?>> bindings;

    public SqlFilterBuilder(@NonNull List<SqlFilterBinding<F, ?>> bindings) {
        this.bindings = List.copyOf(bindings);
    }

    public String build(@NonNull MapSqlParameterSource params, @NonNull F filter, @NonNull String prefix) {
        String dynamic = build(params, filter);
        return dynamic.isBlank() ? "" : prefix + dynamic;
    }

    public String build(@NonNull MapSqlParameterSource params, @NonNull F filter) {
        List<SqlCondition<?>> sqlConditions = bindings.stream()
                .<SqlCondition<?>>map(r -> r.getCondition(filter))
                .filter(Objects::nonNull)
                .toList();
        toParams(sqlConditions).forEach(params::addValue);
        return toSql(sqlConditions);
    }
```

  with the new text

```java
    private final List<SqlFilterBinding<F, ?>> bindings;

    public SqlFilterBuilder(@NonNull List<SqlFilterBinding<F, ?>> bindings) {
        this.bindings = List.copyOf(bindings);
    }

    public String build(@NonNull MapSqlParameterSource params, @NonNull F filter, @NonNull String prefix) {
        List<SqlCondition<?>> sqlConditions = bindings.stream()
                .<SqlCondition<?>>map(r -> r.getCondition(filter))
                .filter(Objects::nonNull)
                .toList();
        toParams(sqlConditions).forEach(params::addValue);
        String dynamic = toSql(sqlConditions);
        return dynamic.isBlank() ? "" : prefix + dynamic;
    }
```

- **Exact change in `SqlFilterBuilderTest.java` (F4 + F6):** write the file with exactly this content:

```java
package org.ost.query.filter;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Covers {@link SqlFilterBuilder#build}: joined conditions behind the prefix, their params, and the duplicate-parameter guard. */
class SqlFilterBuilderTest {

    @Test
    void build_uniqueFilterProperties_joinsConditionsAndParams() {
        SqlFilterBuilder<String> builder = new SqlFilterBuilder<>(List.of(
                SqlBoundFilter.of("title",  "a.title",  (m, v) -> SqlCondition.equalsTo(m, v)),
                SqlBoundFilter.of("status", "a.status", (m, v) -> SqlCondition.equalsTo(m, v))
        ));
        MapSqlParameterSource params = new MapSqlParameterSource();

        String sql = builder.build(params, "irrelevant", "WHERE ");

        assertThat(sql).isEqualTo("WHERE a.title = :title AND a.status = :status");
        assertThat(params.getValue("title")).isEqualTo("irrelevant");
        assertThat(params.getValue("status")).isEqualTo("irrelevant");
    }

    @Test
    void toParams_duplicateFilterPropertyBothPresent_throwsWithClearMessage() {
        SqlFilterBuilder<String> builder = new SqlFilterBuilder<>(List.of(
                SqlBoundFilter.of("title", "a.title",       (m, v) -> SqlCondition.equalsTo(m, v)),
                SqlBoundFilter.of("title", "a.other_title", (m, v) -> SqlCondition.equalsTo(m, v))
        ));

        assertThatThrownBy(() -> builder.build(new MapSqlParameterSource(), "irrelevant", "WHERE "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate filterProperty");
    }
}
```

  If F4 was not approved, use the same content but call `builder.build(params, "irrelevant")` /
  `builder.build(new MapSqlParameterSource(), "irrelevant")` and assert
  `"a.title = :title AND a.status = :status"`.
- **Verification:** `git grep -n "\.build(params, filter)" -- '*.java'` and
  `git grep -n "extends SqlFilterBuilder" -- '*.java'` find nothing; all production callers already use the
  3-argument form (Advertisement, AuditLog, Feedback, ProviderProfile, User repositories). Build in step 9.
- **Sources:** https://martinfowler.com/bliki/Yagni.html

#### Step 5 -- check-batch for the code and test steps

- **Depends on:** steps 1-4 (those that ran).
- **Files:** none changed.
- **Executor:** main thread (a script, no model).
- **Input:** the working tree.
- **Exact change:** run `bash .claude/commands/optimize/check-batch.sh query-lib/src` and keep its
  `TOTAL` line for the inventory's `**Actual:**`.
- **Verification:** exit code 0, no `VIOLATION` line. The `TOTAL` net should be close to -56 when every
  step ran.
- **Sources:** none.

#### Step 6a -- README, variant A: correct the stale statements (D1)

- **Depends on:** decision 2 = A. Runs after steps 1-4 so the README matches the final code.
- **Files:** `query-lib/README.md`
- **Executor:** haiku
- **Input:** the current file.
- **Exact change** (five replacements; old text first, then new text):
  1. Old: `  SqlFilterBinding<F, R>     — @FunctionalInterface: getCondition(F filter) → SqlCondition<R>`
     New: `  SqlFilterBinding<F, R>     — interface: getCondition(F filter) → SqlCondition<R>, plus SqlFilterMapping's accessors`
  2. Old (two lines):
     `  OrderByBuilder             — converts Spring Sort into an ORDER BY clause via an alias→expression map`
     `  SortField                  — record consumed by OrderByBuilder's tiebreaker-aware overload`
     New (two lines):
     `  OrderByBuilder             — converts Spring Sort into an ORDER BY clause from a SortField list`
     `  SortField                  — one sortable property: SQL expression, optional default direction, tiebreakers`
  3. Old: `    String orderBy = OrderByBuilder.build(pageable.getSort(), SORT_MAP);`
     New: `    String orderBy = OrderByBuilder.build(pageable.getSort(), SORT_FIELDS);`
  4. Replace the whole `## OrderByBuilder` section, from the line `## OrderByBuilder` up to (not including)
     the `---` line before `## PaginationSqlBuilder`, with:

````
## OrderByBuilder

```java
private static final List<SortField> SORT_FIELDS = List.of(
        SortField.of(AdvertisementInfoDto.Fields.title,     "a.title"),
        SortField.of(AdvertisementInfoDto.Fields.createdAt, "a.created_at",
                SortField.of(AdvertisementInfoDto.Fields.id, "a.id")));

OrderByBuilder.build(sort, SORT_FIELDS)
// → " ORDER BY a.created_at ASC NULLS LAST, a.id DESC NULLS LAST"  or  ""
```

Property names, tiebreakers and default directions: see `.claude/rules/query-lib.md`.

````

  5. Old: `- `lombok` — `@NonNull`/`@RequiredArgsConstructor`/`@NoArgsConstructor` on the builder classes.`
     New: `- `lombok` (optional, compile time) — null checks and generated constructors.`
- **Verification:** `grep -n "SORT_MAP\|alias\|FunctionalInterface\|tiebreaker-aware" query-lib/README.md`
  finds nothing; the example output matches `OrderByBuilderTest.buildFields_fieldWithTiebreaker_...`.
- **Sources:** none (repository facts: `OrderByBuilder.java:21` is the only `build` method).

#### Step 6b -- README, variant B: rewrite to the project's README standard (D1)

- **Depends on:** decision 2 = B; runs after steps 1-4. Step 7 must then also run (the rule file points
  to the README's factory table, which B removes).
- **Files:** `query-lib/README.md`; if a fact moved out of the README is not yet in a class's own
  Javadoc, also that class (`SqlCondition.java`, `OrderByBuilder.java`), Javadoc only.
- **Executor:** sonnet
- **Input:** `.claude/skills/module-readme-standards/SKILL.md`, `.claude/skills/module-doc-standards/SKILL.md`,
  every file in `query-lib/src/main`, `query-lib/pom.xml`, the current README.
- **Exact behavior:** rewrite the README to the standard: a one-paragraph purpose; how a repository uses
  the classes together (filter DTO → `SqlFilterBuilder` → `SqlBoundFilter` → `SqlCondition` →
  `SqlOperator` template; `Sort` → `OrderByBuilder` with `SortField`s; `Pageable` → `PaginationSqlBuilder`;
  `OffsetPageable` for raw offsets), verified by reading the code; one usage example that compiles against
  the current API (`build(params, filter, prefix)`, `SORT_FIELDS`); a Dependencies section at module
  level. Drop the per-class list and the factory-method table when the class Javadoc already states the
  same fact; move any fact that exists only in the README into the class Javadoc first (one line per
  method comment rule of `.claude/rules.md`). No ADR numbers, no task numbers, links to real files as
  markdown links.
- **Constraints:** no code change beyond Javadoc; do not restate `.claude/rules/query-lib.md`.
- **Verification:** every class named in the README exists (`ls`); every code example matches a real
  signature; a fresh reviewer agent per the skill's "Independent review" section confirms nothing the
  module provides is missing.
- **Sources:** none (project standard).

#### Step 7 -- rule file: remove the removed alias-map overload (D2)

- **Depends on:** decision 3 = yes (always required with 6b).
- **Files:** `.claude/rules/query-lib.md`
- **Executor:** sonnet
- **Input:** the current file, `OrderByBuilder.java`, `SortField.java`, the README after step 6.
- **Exact behavior:**
  - Remove the "Defining a sort-alias map" subsection and every reference to an alias map or a
    `build(sort, aliasToExpression)` overload (the sentence at the top of "Defining a filter", the
    "alternative to the plain alias-map overload" wording, the last sentence of the file).
  - Keep the rule that `SortField` properties are typed `Fields.*` constants, never raw strings, and the
    note on using fully qualified `Fields.*` when two field sets share names; move both under "Sorting
    with a stable tiebreaker".
  - Replace the empty-`Sort` sentence with what the code does: the requested `Sort` orders first; every
    `SortField` that declares a direction is then appended (also when the `Sort` is not empty); each
    field's tiebreakers follow it, skipping any property already used. Do not judge whether this is
    intended (that is decision 4).
  - Replace the list of repository names with no list ("used by every repository that sorts"), so the
    count cannot go stale.
  - If 6b ran: point "SqlCondition factory methods" at the class's own Javadoc instead of the README table.
  - Keep the `paths:` frontmatter unchanged (so `check-rule-path-globs.sh` is not needed).
- **Verification:** `grep -n -i "alias" .claude/rules/query-lib.md` finds nothing; every code example
  compiles against the current API by reading.
- **Sources:** none (repository facts).

#### Step 8 -- pom.xml comment (D3)

- **Depends on:** nothing.
- **Files:** `query-lib/pom.xml`
- **Executor:** haiku
- **Exact change:** old line
  `        <!-- Spring Data JDBC — Sort, Pageable, NamedParameterJdbcTemplate -->`
  new line
  `        <!-- Sort and Pageable (spring-data-commons, through this starter) -->`
  No other line in the pom changes; the dependency set stays as it is.
- **Verification:** `git diff query-lib/pom.xml` shows one changed line and no `<dependency>` line.
- **Sources:** https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html (the
  explicit `spring-jdbc` declaration stays)

#### Step 9 -- verification of the whole

- **Depends on:** all steps that ran.
- **Executor:** main thread (scripts, no model).
- **Exact actions:**
  1. `bash .claude/commands/optimize/check-batch.sh query-lib .claude/rules/query-lib.md` -- exit 0, no
     `VIOLATION`; record `TOTAL` in each entry's `**Actual:**`.
  2. Through the activity monitor (Scripts section of `.claude/rules.md`):
     `bash scripts/build-and-test.sh --unit-test query-lib --skip-vaadin`; it builds the whole reactor, so
     every consumer of the removed overloads is compiled. Expected: query-lib tests pass; test count
     drops by 4 when F1, F2 and F5 all ran (from the current count, which the executor records first).
  3. Then the Definition of Done suite: `bash scripts/build-and-test.sh --unit --integration --sandbox`
     (no UI change, so no Playwright run).
  4. `git grep -n -e "SORT_MAP" -e "SqlCondition.of(" -- query-lib .claude/rules/query-lib.md` finds nothing.
- **Not part of this plan:** decisions 4 and 5 (behavior of `OrderByBuilder`, the starter dependency) and
  the stale line in `integration-tests/README.md`; propose them for the standing deferred-findings task.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a (the planning run is counted in improvement-225)
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
