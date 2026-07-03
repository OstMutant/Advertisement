# feature-001: Field Validation Coverage — UI / DTO / DB Alignment

**Type:** improvement — validation consistency, UX correctness

---

## Context

Audit of all form fields across three layers revealed gaps where constraints exist in one
layer but not another. The goal is to make the DTO the single source of truth for size
constraints, align UI min/max values with those constraints, and ensure UX at boundary
values is predictable and visible to the user.

---

## Layer Alignment Audit

### String Fields

| Domain | Field | DB | UI maxLength | DTO constraint | Status |
|--------|-------|----|-------------|----------------|--------|
| User | name | VARCHAR(255) | 255 + `StringLengthValidator(1,255)` | `@Size(min=1,max=255)` | ✅ Aligned |
| User | email | VARCHAR(255) | read-only in edit form | `@Email @NotBlank` in SignUpDto | ✅ OK (immutable) |
| Advertisement | title | VARCHAR(255) NOT NULL | 255 + `StringLengthValidator(1,255)` | `@NotBlank @Size(min=1,max=255)` | ✅ Fixed |
| Advertisement | description | TEXT (unlimited) | no maxLength, `asRequired()` only | `@NotBlank` | ✅ Fixed (max deferred — see P2) |
| Advertisement | categoryIds | no DB limit | `maxSelectedItemsCount(10)` | `@Size(max=10)` | ✅ Fixed |
| Taxon | name EN/UK | VARCHAR(255) NOT NULL | 255 + `StringLengthValidator(1,255)` | ❌ none in TaxonEditDto | ✅ binder enforces |
| Taxon | description EN/UK | VARCHAR(2000) NOT NULL | 2000 + `StringLengthValidator(1,2000)` | ❌ none in TaxonEditDto | ✅ binder enforces |

### Integer Fields

| Domain | Field | DB | UI min/max | DTO constraint | Status |
|--------|-------|----|-----------|----------------|--------|
| Settings | adsPageSize | JSONB (no column limit) | `setMin(5)` / `setMax(100)` | `@Min(5) @Max(100)` | ✅ Fixed |
| Settings | usersPageSize | JSONB | `setMin(5)` / `setMax(100)` | `@Min(5) @Max(100)` | ✅ Fixed |
| Settings | timelinePageSize | JSONB | `setMin(5)` / `setMax(100)` | `@Min(5) @Max(100)` | ✅ Fixed |

`PaginationDefaults.MIN_PAGE_SIZE = 5` constant used consistently across UI + DTO.

---

## UX at Maximum Values

### Fields with `setMaxLength()` — UiTextField / UiTextArea

Vaadin renders a built-in character counter bottom-right when `maxLength` is set:
- `name`: "254/255" visible as user types → ✅ Good UX
- `taxon.name` (EN/UK): "254/255" → ✅ Good UX
- `taxon.description` (EN/UK): "1999/2000" → ✅ Good UX
- `advertisement.title`: "254/255" → ✅ Good UX

### QuillEditor (advertisement.description)

- **No `maxLength` is set** — no character counter, no limit enforced
- **No `StringLengthValidator`** — `@NotBlank` only (DTO-level empty check)
- DB column is `TEXT` — no limit at DB level either
- If we add a max (e.g. 50 000 chars): QuillEditor has no built-in counter UI →
  **requires a custom character counter implementation** before enforcing a limit makes UX sense
- **Decision required:** what is the intended max for description? Until decided — defer.

### Display in View Mode

| Location | Behaviour | UX at max |
|----------|-----------|-----------|
| Overlay breadcrumb (title) | `text-overflow: ellipsis; white-space: nowrap` | Long title truncated → ✅ |
| Overlay view body (description) | `overflow-wrap: break-word; overflow-x: hidden` | Wraps, no truncation — long text takes more space but is fully readable |
| Activity feed change values | collapsible after 150 chars → Show more/less toggle | ✅ handled |
| Card grid (title) | CSS truncation via `line-clamp` | ✅ handled |

---

## Implemented Fixes

### ✅ P1 — Settings `setMin` aligned + binder range validator

**Files changed:**
- `marketplace-app/.../settings/SettingsFormModeHandler.java`
  - All three fields: `setMin(1)` → `setMin(PaginationDefaults.MIN_PAGE_SIZE)` (5)
  - Extracted `bindPageSizeField(field, getter, setter)` — removes triple validator duplication
  - Binder chain now includes `.withValidator(v -> v >= MIN && v <= MAX, i18n(SETTINGS_PAGE_SIZE_RANGE))`
- `marketplace-app/.../i18n/I18nKey.java` — `SETTINGS_PAGE_SIZE_RANGE("settings.page.size.range")`
- `messages_en.properties` — `settings.page.size.range=Must be between 5 and 100`
- `messages_uk.properties` — `settings.page.size.range=Має бути від 5 до 100`

**Why:** Vaadin IntegerField clamps silently via step buttons but a typed value outside range
triggers no visible error without a binder validator. Entering `2` and blurring snaps to 5
without feedback — confusing UX.

---

### ✅ P1 — DTO constraints on `AdvertisementSaveDto` + `@Valid` wired

**Files changed:**
- `platform-commons/.../advertisement/dto/AdvertisementSaveDto.java`
  ```java
  public record AdvertisementSaveDto(
      Long id,
      @NotBlank @Size(min = 1, max = 255) String title,
      @NotBlank String description,
      @Size(max = 10) Set<Long> categoryIds
  ) {}
  ```
- `advertisement-spring-boot-starter/.../AdvertisementService.java`
  - `save(@NonNull @Valid AdvertisementSaveDto dto, ...)` — triggers JSR-380 on the DTO

**Why:** Constraints existed only in the UI binder. A raw API call bypassed all validation —
any title length and empty description were accepted silently.

---

### ⏳ P1 — `SettingsFormModeHandler` duplication fix (pending)

Extract validator lambda into `private void bindPageSizeField(IntegerField, getter, setter)`.
Currently the same `.withValidator(...)` lambda is copy-pasted three times.

---

## Test Coverage — Boundary Value Tests

**Decision:** no new spec files. Tests added as new `describe` blocks (gated on `PW_FULL`)
inside existing spec files, grouped by behaviour similarity.

### `03-marketplace-promotion-flow.spec.js` — new `describe` block at end

Gated: `test.skip(!process.env.PW_FULL, ...)`

| Test | What it verifies |
|------|-----------------|
| `adminEn signs up maxEn — max name 255 chars, activity created` | Signup + admin renames user to 255-char name, activity diff recorded |
| `adminEn signs up maxUk — max name 255 chars, activity created` | Same for UK user |
| `adminEn seeds 10 boundary categories — for max category selection` | Creates `Boundary-01` … `Boundary-10` used in spec 04 max tests |

### `04-marketplace-advertisement-flow.spec.js` — new `describe` block at end

Gated: `test.skip(!process.env.PW_FULL, ...)`

| Test | What it verifies |
|------|-----------------|
| `maxEn creates max-content EN advertisement — 255-char title, all Quill formats, 10 categories, YouTube + image + video, lightbox, activity` | All fields at boundary, gallery complete, lightbox navigable, v1 activity |
| `maxUk creates max-content UK advertisement — 255-char title, all Quill formats, 10 categories, YouTube + image + video, lightbox, activity` | Same for UK user |
| `maxEn edits EN max-content advertisement — discard, new 255-char title, replace all media, activity diff v2` | Edit flow at boundary: discard restores, save creates v2 diff with max-length fields |
| `maxUk edits UK max-content advertisement — discard, new 255-char title, replace all media, activity diff v2` | Same for UK user |

---

## Deferred

### P2 — QuillEditor character counter

Before adding a max length to `advertisement.description`:
1. Decide on the limit (50 000? 10 000?)
2. Implement a character counter in `QuillEditor.java` that reads raw text length from Quill
   (via JS interop: `quill.getText().length`)
3. Show counter below editor, same style as Vaadin TextArea counter
4. Only then add `StringLengthValidator` to the binder and `@Size(max=N)` to DTO

**Blocked until the limit is decided.**

### P3 — `advertisement.description` DB schema

Change `description TEXT` → `description VARCHAR(N)` in advertisement table once the limit
from P2 is decided. Requires a Liquibase migration.

---

## Key Files

| File | Role |
|------|------|
| `platform-commons/.../advertisement/dto/AdvertisementSaveDto.java` | `@NotBlank @Size @Size` — done |
| `advertisement-spring-boot-starter/.../AdvertisementService.java` | `@Valid` on `save()` param — done |
| `marketplace-app/.../settings/SettingsFormModeHandler.java` | min=5, range validator, extract bindPageSizeField |
| `marketplace-app/.../i18n/I18nKey.java` | `SETTINGS_PAGE_SIZE_RANGE` — done |
| `marketplace-app/src/main/resources/i18n/messages_en.properties` | range message — done |
| `marketplace-app/src/main/resources/i18n/messages_uk.properties` | range message — done |
| `marketplace-app/.../fields/QuillEditor.java` | Future: character counter (P2) |
| `playwright/e2e/03-marketplace-promotion-flow.spec.js` | Add max-user + boundary-categories describe block |
| `playwright/e2e/04-marketplace-advertisement-flow.spec.js` | Add max-content create + edit describe block |
