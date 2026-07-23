# improvement-072: Generics/type-safety design debt — `UiComponentFactory<T>`'s dual role, `AuditReadService`'s raw hook dispatch, `AuditDomainHookImpl.castIfKnown`'s missing type token

**Type:** improvement — design-level type-safety debt, needs a design decision before implementing,
not a mechanical fix. Found via external review discussion, verified and re-scoped against current
source (2026-07-16) — the originally proposed one-line fixes for these three do not compile/apply
as first suggested; this issue captures the real, larger scope after verification.
**Module:** `marketplace-app` (`ui/core/UiComponentFactory.java`, `spi/AuditDomainHookImpl.java`),
`audit-spring-boot-starter` (`services/AuditReadService.java`), `platform-commons`
(`audit/spi/AuditDomainHook.java`, `audit/spi/AuditPort.java`).
**Priority:** low — each of the three has a working `@SuppressWarnings`-scoped escape hatch today
with no observed correctness bug; genuinely useful to resolve for cleanliness and to keep the
pattern from being copied uncritically into new domains (e.g. a future payment-starter), but not
urgent.
**When:** independent, no blockers. Needs a design decision (see each item) before implementation,
not just an approval to start coding.

## Problem — three separate items, each verified individually

### 1. `UiComponentFactory<T>` serves two different roles under one type

`UiComponentFactory<T>.build(params)` casts `get()` to `Configurable<T, P>` under
`@SuppressWarnings("unchecked")`. A tempting fix is bounding the class itself:
`UiComponentFactory<T extends Configurable<T, ?>>`. **Verified this does not compile as proposed**:
`AuditTimelineListRenderer`, `AuditActivityListRenderer`, `AuditActivityRowRenderer`,
`AuditTimelineRowRenderer` are all injected as `UiComponentFactory<X>` (see
`MarketplaceUiConfiguration`) and used only via the inherited `.get()` — none of them implement
`Configurable`. `UiComponentFactory<T>` is being used as a plain alias for `ComponentFactory<T>` in
these four cases, contradicting `marketplace-app/CLAUDE.md`'s own stated rule ("Use
`UiComponentFactory<T>` for Configurable prototype UI beans... Use `ComponentFactory<T>` for
optional singleton services").

**Design decision needed:** either (a) migrate those four non-Configurable consumers to plain
`ComponentFactory<T>` first (the architecturally "correct" fix per the existing documented rule),
*then* add the `Configurable` bound to `UiComponentFactory<T>` — a real, if small, migration across
4 call sites; or (b) accept `UiComponentFactory<T>` keeps serving both roles and the unchecked cast
in `build()` stays, since it's the only method that actually needs the `Configurable` assumption.

### 2. `AuditReadService`'s raw `List`/`AuditActivityEnrichHook` usage is a known Java generics limitation, not sloppiness

`getEntityActivity()`/`getTimelinePage()` use a raw `List` and iterate `activityEnrichHooks` (typed
`List<AuditActivityEnrichHook>`, raw) under `@SuppressWarnings({"unchecked", "rawtypes"})`.
`AuditActivityEnrichHook<T extends AuditableSnapshot>` is invariant and documented as "one bean per
entity type" — a genuinely heterogeneous collection of hooks each bound to a *different* concrete
`T`, dispatched at runtime via `hook.entityType() == entityType`. **Verified the commonly-suggested
fix (`for (AuditActivityEnrichHook<AuditableSnapshot> hook : activityEnrichHooks)`) does not
compile**: Java generics are invariant, so `AuditActivityEnrichHook<AdvertisementSnapshotDto>` is
not a subtype of `AuditActivityEnrichHook<AuditableSnapshot>`, and there's no way to iterate a
`List<AuditActivityEnrichHook<?>>` and call a `T`-typed method on each element without either a
helper method that recaptures each element's own wildcard, or exactly the raw-type dispatch already
in place.

**Design decision needed:** whether the standard "wildcard-capture helper method" idiom (a private
generic method invoked once per hook, letting the compiler infer each hook's own `T` independently)
is worth introducing here to eliminate the `@SuppressWarnings`, or whether the current raw-type
approach is accepted as the pragmatic idiom for this specific "runtime-dispatched heterogeneous
collection" shape — this is a real Java language limitation, not a fixable design mistake.

### 3. `AuditDomainHookImpl.castIfKnown()` has no type token to actually verify the caller-requested `T`

```java
public <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> castIfKnown(
        @NonNull AuditSnapshotContentDto<? extends AuditableSnapshot> content) {
    ...
    case AdvertisementSnapshotDto _, UserSnapshotDto _, SettingsSnapshotDto _, TaxonSnapshotDto _ ->
            Optional.of((AuditSnapshotContentDto<T>) content);
```
The `switch` only confirms `content.snapshotData()` is *one of* the four known snapshot types — it
never confirms it matches the *specific* `T` the caller asked for. A caller inferring `T =
TaxonSnapshotDto` when the actual runtime data is `AdvertisementSnapshotDto` would still match a
case arm and get an unchecked cast that "succeeds" (type erasure), silently returning
mismatched-type content instead of failing fast.

**Verified the scope of the standard fix (`Class<T> targetClass` type-token parameter) is larger
than a single-method change**: `T` in `castIfKnown()` is inferred all the way from
`AuditPort.getSnapshotContent(Long snapshotId, EntityType entityType)` → `DefaultAuditPort` →
`AuditDomainHook.castIfKnown()`, with no `Class<T>` available anywhere in that chain today. Adding
a type token means changing `AuditPort.getSnapshotContent()`'s signature (a `platform-commons`
interface) and every one of its callers, not just this one method.

**Design decision needed:** is this worth the interface-signature ripple, given `getSnapshotContent`
is only actually called with entity-type-scoped context already known at each call site (so a
`Class<T>` argument would usually just be a literal at the call site, not hard to supply — but still
touches a public SPI contract other modules depend on).

## Suggested approach

Given all three need a design decision rather than a mechanical fix, and none currently has an
observed correctness bug, treat this as a single design/discussion pass (not three independent
PRs) — the `UiComponentFactory<T>` question in particular affects how the other two are best
approached (e.g. if `ComponentFactory<T>` vs `UiComponentFactory<T>` usage gets clarified project-wide,
that context is useful before deciding whether the audit-side hook/cast patterns are worth
touching too).

## Related

- `backlog/issues/improvement-071-taxonformoverlaymodehandler-raw-uicomponentfactory.md` — the one
  part of the original review finding that *was* a mechanical, low-risk fix, filed separately.
- `marketplace-app/CLAUDE.md` — the `Configurable`/`UiComponentFactory<T>` vs `ComponentFactory<T>`
  usage rule item 1 already contradicts in practice.
- `platform-commons/src/main/java/org/ost/platform/audit/spi/AuditDomainHook.java`,
  `AuditPort.java` — the SPI signatures item 3's type-token fix would need to change.
