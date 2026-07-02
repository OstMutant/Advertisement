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
| Advertisement | title | VARCHAR(255) NOT NULL | 255 + `StringLengthValidator(1,255)` | ❌ none | ⚠️ DTO gap |
| Advertisement | description | TEXT (unlimited) | no maxLength, `asRequired()` only | ❌ none | ❌ Gap in all layers |
| Advertisement | categoryIds | no DB limit | `maxSelectedItemsCount(10)` | ❌ none | ⚠️ DTO gap |
| Taxon | name EN/UK | VARCHAR(255) NOT NULL | 255 + `StringLengthValidator(1,255)` | ❌ none in TaxonEditDto | ✅ binder enforces |
| Taxon | description EN/UK | VARCHAR(2000) NOT NULL | 2000 + `StringLengthValidator(1,2000)` | ❌ none in TaxonEditDto | ✅ binder enforces |

### Integer Fields

| Domain | Field | DB | UI min/max | DTO constraint | Status |
|--------|-------|----|-----------|----------------|--------|
| Settings | adsPageSize | JSONB (no column limit) | `setMin(1)` / `setMax(100)` | `@Min(5) @Max(100)` | ❌ UI min wrong (1 vs 5) |
| Settings | usersPageSize | JSONB | `setMin(1)` / `setMax(100)` | `@Min(5) @Max(100)` | ❌ UI min wrong (1 vs 5) |
| Settings | timelinePageSize | JSONB | `setMin(1)` / `setMax(100)` | `@Min(5) @Max(100)` | ❌ UI min wrong (1 vs 5) |

`PaginationDefaults.MIN_PAGE_SIZE = 5` already exists — the constant just isn't being used.

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
- **No `StringLengthValidator`** — `asRequired()` only
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

## Fixes

### P1 — Fix Settings `setMin` + add binder range validator

**Files:**
- `marketplace-app/.../settings/SettingsFormModeHandler.java`
  - All three fields: `setMin(1)` → `setMin(PaginationDefaults.MIN_PAGE_SIZE)` (5)
  - Add to each binder chain:
    ```java
    .withValidator(
        v -> v >= PaginationDefaults.MIN_PAGE_SIZE && v <= PaginationDefaults.MAX_PAGE_SIZE,
        getValue(SETTINGS_PAGE_SIZE_RANGE)
    )
    ```
- `marketplace-app/.../i18n/I18nKey.java` — add `SETTINGS_PAGE_SIZE_RANGE("settings.page.size.range")`
- `messages_en.properties` — `settings.page.size.range=Must be between 5 and 100`
- `messages_uk.properties` — `settings.page.size.range=Має бути від 5 до 100`

**Why:** Vaadin IntegerField clamps silently via step buttons but a typed value outside range
triggers no visible error without a binder validator. Entering `2` and blurring snaps to 5
without feedback — confusing UX.

---

### P1 — Add DTO constraints to `AdvertisementSaveDto` + wire `@Valid`

**Files:**
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
  - `save(@NonNull AdvertisementSaveDto dto, ...)` → `save(@NonNull @Valid AdvertisementSaveDto dto, ...)`
  - Without `@Valid` the JSR-380 annotations never fire even though the class is `@Validated`

**Why:** Currently constraints exist only in the UI binder. A raw API call bypasses all
validation — any title length and empty description are accepted.

---

### P2 — QuillEditor character counter (separate feature, UI decision required)

Before adding a max length to `advertisement.description`:
1. Decide on the limit (50 000? 10 000?)
2. Implement a character counter in `QuillEditor.java` that reads raw text length from Quill
   (via JS interop: `quill.getText().length`)
3. Show counter below editor, same style as Vaadin TextArea counter
4. Only then add `StringLengthValidator` to the binder and `@Size(max=N)` to DTO

**This is blocked until the limit is decided.**

---

### P3 — `advertisement.description` DB schema (deferred)

Change `description TEXT` → `description VARCHAR(N)` in advertisement table once the limit
from P2 is decided. Requires a Liquibase migration.

---

## Test Coverage Plan (not implemented yet)

No new spec files needed. Steps can be added as `test.step()` blocks inside existing flows:

| What to test | Where to add | Existing test |
|-------------|-------------|---------------|
| Empty title → save rejected, inline error | `advertisement.flow.js` `runCreateAdvertisementFlow` — before the real fill | `04-marketplace-advertisement-flow.spec.js:42` |
| Settings min clamp — type `2`, blur → shows `5` | `05-seed-filter-sort-pagination.spec.js:341` before the main page-size changes | "adminEn changes page sizes" |
| Settings max clamp — type `999`, blur → shows `100` | same test | same |
| Category max 10 — 11th selection rejected | `advertisement.flow.js` — requires pre-seeding 10+ categories | needs 10 categories in DB first |

**Constraint:** Only `test.step()` inside existing tests — no new spec files.

---

## Key Files

| File | Role |
|------|------|
| `platform-commons/.../advertisement/dto/AdvertisementSaveDto.java` | Add `@NotBlank @Size @Size` |
| `advertisement-spring-boot-starter/.../AdvertisementService.java` | Add `@Valid` on `save()` param |
| `marketplace-app/.../settings/SettingsFormModeHandler.java` | Fix min=5, add range validator |
| `marketplace-app/.../i18n/I18nKey.java` | Add `SETTINGS_PAGE_SIZE_RANGE` |
| `marketplace-app/src/main/resources/i18n/messages_en.properties` | Add range message |
| `marketplace-app/src/main/resources/i18n/messages_uk.properties` | Add range message |
| `marketplace-app/.../fields/QuillEditor.java` | Future: add character counter (P2) |
| `playwright/e2e/_flows/advertisement.flow.js` | Future: add validation step.step() (P1 test) |
| `playwright/e2e/05-seed-filter-sort-pagination.spec.js` | Future: add settings clamp step.step() |
