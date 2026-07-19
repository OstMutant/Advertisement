# improvement-102: AttachmentMediaChangeHook has zero implementations — remove it (or re-justify keeping it)

**Type:** improvement — simplification / YAGNI, decision issue. Found via simplification review
(2026-07-19).
**Module:** `platform-commons` (`attachment.spi.AttachmentMediaChangeHook`),
`attachment-spring-boot-starter` (`AttachmentService` fire sites)
**Priority:** low-medium — no bug; carrying a dead extension point costs comprehension on every
read of `AttachmentService` and invites cargo-cult copies in future starters
**When:** Batch M (attachment API simplification) — see `backlog/BACKLOG.md` "Execution batches"

## Problem

`AttachmentMediaChangeHook.onMediaChanged(EntityRef)` fires from `AttachmentService` on every
media mutation (`notifyMediaChanged()` — 7 call sites), but has had **zero implementations**
since ADR-035 removed `MediaChangeHookImpl` (the denormalized-media sync it fed no longer
exists). ADR-035 documented "no implementation" as a valid, gracefully-degraded state — true for
runtime, but it now contradicts `platform-commons/CLAUDE.md`'s own governance rule: "Random
abstractions without ≥2 cross-module consumers" are not allowed there, and this one has zero.

Every reader of `AttachmentService` must trace the hook to discover it does nothing; every future
starter author sees a fire-an-event-nobody-hears pattern presented as normal.

## Suggested fix

**Option A (recommended, per YAGNI + the governance rule):** delete the interface, the
`ObjectProvider` field, and all 7 `notifyMediaChanged()` calls. Git history preserves the shape
if a real consumer ever appears; re-adding a one-method SPI + fire sites is a ~30-minute change.
Annotate ADR-035 (per the DECISIONS.md update rule) rather than only recording a new entry.

**Option B:** keep it, but then write down the concrete anticipated consumer in ADR-035 (what
will implement it and when) — "forward-looking" only counts with a named future.

## Related

- `marketplace-app/DECISIONS.md` ADR-035 — must be annotated whichever option wins.
- `platform-commons/CLAUDE.md` "NOT ALLOWED" list — the governance rule this resolves against.
