## ⛔ RE-READ ALL RULES BEFORE EVERY ACTION
Before executing any tool call — re-read this entire file. No exceptions.

---

> ## ⛔ NEVER commit without explicit user request
> `git commit` is **forbidden** unless the user says "зроби коміт", "commit", or equivalent.
> `git add` runs automatically after every file change — commit does NOT.
> Violating this rule has happened multiple times. No exceptions.

> ## ⛔ Code comments: one line or none, never an issue/ticket number
> Every code comment (production code and test code alike) is either **one line** or **not
> written at all**. Never a multi-line block explaining background/rationale in full — that
> belongs in the issue file, ADR, or commit message. Also never mention an issue/ticket number
> (`improvement-NNN`, etc.) inside a code comment — it looks bad and rots as issues get renumbered
> or archived; that traceability belongs in the commit message, not the code. Write the one-line,
> number-free version on the first pass; do not wait to be told to fix it. Violating this rule has
> happened repeatedly.

## Approval Rule
**Every action must be approved by the user before execution — no exceptions.**

Before doing anything, present the plan in two layers, in this order:

1. **Plain-language layer (first):** why this is being done and what the outcome will be, in
   words a non-technical reader could follow — no file paths, no method signatures yet.
2. **Technical layer (after):** the exact instruction you would give yourself to execute the
   action — full file paths, exact changes (method signatures, SQL, config values, field names),
   any side-effects or follow-up steps.

Present both layers, then **STOP and wait for explicit confirmation** before executing.

Example format:
> Plain-language: "The activity tab shows the wrong reviewer, so admins can't tell who actually
> approved a change. Fixing it so the correct reviewer's name always shows."
>
> Technical: "Edit `/full/path/File.java`: replace method `getMediaActivity(Long userId)` with
> `merge(Long userId, List<ActivityItemDto> baseItems)` — do it?"

Wait for explicit confirmation before making any change.

## Module Import Rules

**No direct imports between sibling modules.**
- Starters must NOT import from marketplace or from each other.
- Marketplace may import from starters only via platform-commons contracts (Ports/Hooks/DTOs)
  and published UI components — never via internal impl classes (util, service, repository).

## Git Workflow
- `git add` — run automatically after every file change
- `git commit` — **ONLY** when the user explicitly says to commit — never otherwise

## Language
All repository content must be in **English**: code comments, Javadoc, README files, commit messages, Playwright test descriptions, and any other text checked into the repository.

## `.bat` files — ASCII only, no em-dashes or other Unicode punctuation
`cmd.exe` reads `.bat` files in a legacy codepage, not UTF-8. A multi-byte UTF-8 character (em-dash
`—`, smart quotes, etc.) anywhere in the file — even inside a `::`/`REM` comment — can corrupt
`cmd.exe`'s own batch-label parsing, producing `The system cannot find the batch label specified`
errors on real Windows for labels that objectively exist in the file (confirmed directly: an
em-dash added to a comment in `scripts/collect-code.bat` broke `call :FindFiles`/`call :CountFiles`
elsewhere in the same file). Use plain ASCII `-`/`--` instead of `—`/`–` in every `.bat` file,
including comments. Not an issue in `.sh` files (bash reads UTF-8 natively).

## Test Coverage After Bug Fixes
After fixing a bug, cover all affected flows with Playwright tests before marking the task complete.

## Scripts
Always use project scripts — never raw docker/mvn commands:
- `bash scripts/deploy-dev.sh` — dev deploy (JAR hot-swap, ~3-4 min)
- `bash scripts/deploy.sh` — full rebuild (~7-10 min)
- `bash scripts/playwright.sh [scenario]` — Playwright tests
- `mvn clean test 2>&1 | tee /tmp/test.log` — JUnit tests

**Run all scripts with Monitor + tee pattern:**
1. Launch Monitor (`persistent: true`) watching the log file every 10s — reports stuck/error/success
2. Run synchronously with `timeout: 600000` piped to `tee /tmp/<script>.log`
3. User sees full streaming output directly

**Before running Playwright** — kill old processes first:
1. `docker exec pw-runner pkill -f "node.*playwright" 2>/dev/null; true`
2. Launch Monitor watching `/tmp/playwright.log` (10s interval, catch `failed|Error|passed`)
3. Then run: `bash scripts/playwright.sh [scenario] 2>&1 | tee /tmp/playwright.log`

**Before running deploy.sh** — launch Monitor watching `/tmp/deploy.log` (10s interval, catch `ERROR|BUILD SUCCESS|Started Application`), then run: `bash scripts/deploy.sh [args] 2>&1 | tee /tmp/deploy.log`

## Issue Lifecycle

When filing a **new** issue in `backlog/issues/`:
- Always assign a `**Priority:**` line in the issue file itself — never leave it blank/TBD.
- Always add it to `backlog/BACKLOG.md`'s Priority order table at a ranked position (not just the
  "Still open" listing table) in the same operation — a new issue is never left unranked pending
  future triage. See `backlog/BACKLOG.md`'s "Maintenance rules".

When an issue in `backlog/issues/` is resolved (fix is implemented and committed):
- Move the file to `backlog/completed/issues/` — **immediately, in the same operation as the fix**
- Do not leave resolved issues in `backlog/issues/`
- Remove its row from `backlog/BACKLOG.md` and add a one-line `✅ Done` entry to
  `backlog/completed/BACKLOG-ARCHIVE.md` under the relevant wave — same operation, see
  `backlog/BACKLOG.md`'s "Maintenance rules"

## Definition of Done
A feature or fix is not complete until all of the following hold:
- The relevant full test suite is green: `bash scripts/unit-tests.sh` + `bash scripts/integration-
  tests.sh --sandbox` always; the full Playwright `e2e --full --ux` scenario too whenever the
  change touches UI-visible behavior. `bash scripts/ci.sh` (`/ci`) runs this whole chain
  (unit → integration → e2e → Sonar) in one pass when a single command is preferred over running
  each stage separately.
- `DECISIONS.md` (the relevant module's) is updated if the change is architectural — a new
  decision, or an annotation to an existing one it supersedes.
- The issue file is moved from `backlog/issues/` to `backlog/completed/issues/`, its `BACKLOG.md`
  row removed, and a `✅ Done` entry added to `BACKLOG-ARCHIVE.md` — see "Issue Lifecycle" above.

## After Interruption
After any [Request interrupted by user] — full stop. No further tool calls, no continuation, no fixes.
Wait for the next explicit user message before doing anything.

## Error Reporting
When running any script or command that fails, immediately read the error output and show the specific error lines in the chat. Never just report "it failed" without the actual error details.

---

## Overlay Pattern

### OverlaySession — immutable state machine
Every overlay uses a `record OverlaySession(Mode mode, EntityDto entity, Runnable onSaved, boolean enteredFromView)`.
Mode transitions return new instances — never mutate fields directly:
```java
session = session.toEdit();          // correct
session = session.toView();          // correct
session = session.withEntity(fresh); // correct — after save
session.mode = Mode.EDIT;            // wrong — record, mutation is impossible
```

### switchTo() — the only way to transition between modes
Always call `switchTo()` to transition between overlay modes. Never call `launchSession()`
for transitions — `launchSession()` resets the entire layout and triggers an unnecessary
JS scroll-reset. `launchSession()` is only for the initial overlay open.

```java
// correct — transitioning between modes
session = session.toEdit();
switchTo();

// wrong — resets layout
session = session.toEdit();
launchSession(this::switchTo);
```

### currentFormHandler — reset before switchTo()
At the start of `switchTo()` always reset `currentFormHandler = null` before the switch expression.
Without this, after VIEW→EDIT→VIEW the handler remains non-null and `hasUnsavedChanges()`
returns `true` even though the form is already closed.

```java
@Override
protected void switchTo() {
    currentFormHandler = null;  // always the first line
    OverlayModeHandler handler = switch (session.mode()) { ... };
    ...
}
```

### afterSave() — update entity in session
After saving in EDIT mode always update the entity in the session via `withEntity()`:
```java
if (session.mode() == Mode.EDIT) {
    currentFormHandler.afterSave(true);
    EntityDto fresh = currentFormHandler.getSavedDto();
    if (fresh != null) session = session.withEntity(fresh);
} else {
    closeToList();
}
```

### discardChanges() — the only name for resetting a form
The method for resetting form state in FormOverlayModeHandler is always named `discardChanges()`.
In `doCancel()` of the overlay always call it before transitioning:
```java
if (currentFormHandler != null) currentFormHandler.discardChanges();
```

---

## View Pattern

### init() — structure and visibility
The `@PostConstruct init()` method in all View classes is always `protected`, never `public`.
The order inside init() is fixed:

```java
@PostConstruct
protected void init() {
    // 1. CSS class and sizing
    // 2. Build main component (grid / container)
    // 3. Build contentWrapper
    // 4. add(contentWrapper, overlay)
    // 5. Subscriptions (queryBar, pagination, shortcuts)
    // 6. settingsPaginationBinding.register(...)
    // 7. refresh()
}
```

Do not split init() into small `initXxx()` methods if each does 1–2 lines.
Extract into a separate method only when logic is complex (e.g. Grid column configuration
via a dedicated `*GridConfigurator` class).

### refresh() — always with try/catch
The `refresh()` method in all View classes is always `private` and always guarded:

```java
private void refresh() {
    try {
        // fetch + render logic
    } catch (ConstraintViolationException ex) {
        log.warn("Validation error: {}", ex.getMessage(), ex);
        showValidationErrors(ex);
        clearContent();
        paginationBar.setTotalCount(0);
    } catch (Exception ex) {
        log.error("Failed to refresh view", ex);
        notificationService.error(...);
        clearContent();
        paginationBar.setTotalCount(0);
    } finally {
        queryStatusBar.update(); // if queryStatusBar is present in this view
    }
}
```

Never leave `refresh()` without try/catch — an unhandled exception means a blank screen for the user.

### Refresh method name
The data refresh method in a View is always named `refresh()`, never `refreshGrid()`,
`refreshData()`, or any other variant.

---

## Query Layer Pattern

### FilterMeta and SortMeta — Fields.* constants only
In `*SortMeta` and `*FilterMeta` classes, fields always reference typed `Fields.*` constants
from the DTO, never raw strings:

```java
// correct — compiler catches renames
SortFieldMeta.of(AdvertisementInfoDto.Fields.updatedAt, ADVERTISEMENT_SORT_UPDATED_AT)

// wrong — silent failure on refactoring
SortFieldMeta.of("updatedAt", ADVERTISEMENT_SORT_UPDATED)
```

---

## DTO Field Name Constants

When a DTO needs field name constants (e.g. for `*SortMeta` or `*FilterMeta`), always use
the Lombok `@FieldNameConstants` annotation — never write a manual `Fields` inner class:

```java
// correct — Lombok generates Fields.id, Fields.name, etc.
@FieldNameConstants
public record UserDto(Long id, String name, ...) {}

// wrong — manual boilerplate
public record UserDto(Long id, String name, ...) {
    public static final class Fields {
        public static final String id = "id";
        ...
    }
}
```

`@FieldNameConstants` works on records since Lombok 1.18.22 (project uses 1.18.34+).
Import: `lombok.experimental.FieldNameConstants`.

---

## Form Handler Pattern

### buildTabbedContent() — do not duplicate
Lazy-load tab logic lives in `AbstractFormOverlayModeHandler.buildTabbedContent()`.
Never implement tab-switching manually in concrete handlers — delegate to the base class:

```java
Div content = buildTabbedContent(
    "my-tabs-css-class",
    primaryTab, primaryContent,
    secondaryTab, this::buildSecondaryContent  // lazy loader
);
```

### buildBinder() — separate method
Binder creation and field binding logic is always extracted into a separate `buildBinder(EntityDto dto)`
method, never inlined into `activate()`.

---

## Reference Implementations

When adding a new domain, use these as reference:
- View: `AdvertisementsView` (init structure) + `UserView` (refresh guard)
- Overlay: `AdvertisementOverlay` (OverlaySession, afterSave, mode switching)
- ViewModeHandler: `AdvertisementViewOverlayModeHandler` (AbstractViewOverlayModeHandler)
- FormModeHandler: `UserFormOverlayModeHandler` (buildBinder separate)
- QueryBlock: `AdvertisementQueryBlock` and `UserQueryBlock` (identical structure)
- FilterMeta: `AdvertisementFilterMeta` (Fields.* constants)
- SortMeta: `AdvertisementSortMeta` (Fields.* constants)
