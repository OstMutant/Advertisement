# improvement-108: ILIKE search values are not escaped — `%`, `_`, `\` leak as wildcards

**Type:** bug — query correctness (wildcard injection into ILIKE, not SQL injection). Found via
edge-case review (2026-07-19).
**Module:** `query-lib` (`filter/SqlCondition.like()`, `filter/SqlOperator.LIKE_IGNORE_CASE`)
**Priority:** 🟡 medium — every text filter (advertisement title, user name/email) behaves
unpredictably for any search term containing `%`, `_`, or `\`
**When:** Batch H-adjacent / query-lib touch — standalone; small and self-contained

## Problem

`SqlCondition.like()` wraps the user's value in `%…%` with no escaping:

```java
public static SqlCondition<String> like(SqlFilterMapping m, String value) {
    return applyIfPresent(m, value, SqlOperator.LIKE_IGNORE_CASE, v -> "%" + v + "%");
}
// LIKE_IGNORE_CASE = "%s ILIKE :%s"
```

Postgres `ILIKE` treats `%` and `_` as wildcards and `\` as the escape char. So:

- searching `100%` → `ILIKE '%100%%'` → the trailing `%` is a wildcard: matches "100" followed by
  anything, not the literal "100%";
- searching `a_b` → matches `axb`, `a1b`, … (any single char), not the literal underscore;
- a trailing `\` can even produce a malformed pattern.

Values are bound as parameters, so this is **not** SQL injection — but it is semantic wildcard
leakage: text search silently returns wrong results for common inputs (percentages, snake_case
identifiers, emails with `_`). No caller escapes before calling `like()`; it's the shared helper's
job.

## Suggested fix

Escape LIKE metacharacters inside `like()` before wrapping, and declare the escape char:

```java
String escaped = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
// pattern: "%" + escaped + "%", operator: "%s ILIKE :%s ESCAPE '\\'"
```

Keep it entirely inside `query-lib` so every consumer (advertisement, user, any future domain)
benefits without change. Add unit tests to `SqlConditionTest` for `%`/`_`/`\`/mixed inputs
(query-lib already has plain unit tests, no Docker needed).

## Related

- `query-lib/CLAUDE.md` — the `like()` row in the SqlCondition table should note the escaping
  behavior once fixed.
- `query-lib/DECISIONS.md` — record the ESCAPE-clause choice.
