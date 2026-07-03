# improvement-006: QuillEditor character counter + advertisement.description DB limit

**Type:** improvement — follow-up from feature-001 (field validation coverage)
**Module:** marketplace-app + advertisement-spring-boot-starter
**Priority:** low — depends on `issue-description-length-tag-spam.md` being fixed first

## Problem

`AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH = 2000` is already decided and enforced via a
binder validator in `AdvertisementFormOverlayModeHandler.buildBinder()`, but:

1. **No UI character counter.** `QuillEditor` has no visible counter, unlike Vaadin's
   `TextArea`/`TextField` which show one automatically when `maxLength` is set. Users get no
   feedback on remaining space while typing.
2. **DB schema still `TEXT`, not `VARCHAR(2000)`.** No Liquibase migration enforces the limit
   at the DB level — it's UI/service-layer only.

## Blocked by

`features/issues/issue-description-length-tag-spam.md` — the current validator strips HTML
tags via regex before counting, which is exploitable. Fix that first (Jsoup-based text
extraction) so the counter and the validator measure "length" the same way before building a
counter UI on top of it.

## Suggested fix (in order)

1. Fix the length validator to use Jsoup text extraction (see linked issue).
2. Implement a character counter in `QuillEditor.java` reading `quill.getText().length` via JS
   interop, displayed the same way as the Vaadin TextArea counter.
3. Add a Liquibase migration changing `advertisement.description` from `TEXT` to
   `VARCHAR(2000)`, once the above are consistent.
