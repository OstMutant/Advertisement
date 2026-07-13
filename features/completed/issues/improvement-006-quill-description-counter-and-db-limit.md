# improvement-006: QuillEditor character counter + advertisement.description DB limit

**Status:** ✅ RESOLVED (2026-07-13) — counter added to `QuillEditor` (reads `quill.getText()`,
displays "N / 2000", visually confirmed via Playwright screenshot). DB column changed to
`VARCHAR(20000)`, **not** `VARCHAR(2000)` as originally suggested below — the column stores raw
HTML (with formatting tags), and the already-established raw-size cap is 20000
(`DESCRIPTION_RAW_MAX_LENGTH`, ADR-024), not 2000 (`DESCRIPTION_MAX_LENGTH`, the visible-text
limit). Capping the column at 2000 would have rejected legitimately-formatted descriptions that
already pass every other validation layer. See `marketplace-app/DECISIONS.md` ADR-031. Edited
directly into the existing `01-advertisement-schema.xml` changeset (DB not yet in production).
Full e2e suite 48/48 green.

**Type:** improvement — follow-up from feature-001 (field validation coverage)
**Module:** marketplace-app + advertisement-spring-boot-starter
**Priority:** low — cosmetic (counter) + defense-in-depth (DB limit); no longer blocked
**When:** Wave 2 — unblocked, can be picked up any time

## Problem

`AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH = 2000` is enforced via a binder validator in
`AdvertisementFormOverlayModeHandler.buildBinder()` and (as of 2026-07-04) also via a
Jsoup-based service-level guard in `AdvertisementService`, but:

1. **No UI character counter.** `QuillEditor` has no visible counter, unlike Vaadin's
   `TextArea`/`TextField` which show one automatically when `maxLength` is set. Users get no
   feedback on remaining space while typing.
2. **DB schema still `TEXT`, not `VARCHAR(2000)`.** No Liquibase migration enforces the limit
   at the DB level. Note: a raw-size cap now exists (`@Size(max = DESCRIPTION_RAW_MAX_LENGTH
   = 20_000)` on `AdvertisementSaveDto.description`), which bounds worst-case payload size,
   but the DB column is still unbounded relative to the real 2000-char text limit.

## Formerly blocked by (now resolved)

`features/completed/issues/issue-description-length-tag-spam.md` — the regex-based validator
was replaced with Jsoup text extraction (2026-07-04), consistently at both UI and service
layers. This issue's original reason for being deferred no longer applies — the counter and
the validator now agree on how "length" is measured, so building a counter UI on top of it is
safe to do independently at any time.

## Suggested fix (in order)

1. ~~Fix the length validator to use Jsoup text extraction~~ — done, see
   `features/completed/issues/issue-description-length-tag-spam.md`.
2. Implement a character counter in `QuillEditor.java` reading `quill.getText().length` via JS
   interop, displayed the same way as the Vaadin TextArea counter.
3. Add a Liquibase migration changing `advertisement.description` from `TEXT` to
   `VARCHAR(2000)`, once the above are consistent.
