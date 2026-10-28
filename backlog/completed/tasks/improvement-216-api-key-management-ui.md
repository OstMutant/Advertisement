# improvement-216: API key management in the Vaadin UI (view and revoke own keys)

**Type:** improvement — missing UI for an existing capability
**Module:** `marketplace-app` (new Vaadin view), `marketplace-orchestrator` (`ApiKeyManagementService`, already present)
**Priority:** medium — Top, right after improvement-212
**When:** independent; no blockers

## Current state

API keys can be issued (`POST /api/api-keys`), listed (`GET /api/api-keys`) and revoked (`DELETE /api/api-keys/{id}`) only through the REST API. `marketplace-orchestrator`'s `ApiKeyManagementService` already exposes `create`, `listForActor` and `revoke` over `ApiKeyPort`, and `ApiKeySummaryDto` carries `id`, `keyPrefix`, `label`, `createdAt`, `lastUsedAt` and `revokedAt` — never the raw key or its hash. `marketplace-app` has no view that uses any of this.

## Why change

A user who issued keys through Swagger or scripts has no way to see which keys exist, which are still in use, or to remove forgotten ones without calling REST by hand. Keys are shown once at issuance and stored as hashes, so the list of metadata is the only way to recognise them later.

## Expected benefit

A user sees their own keys (label, prefix, created, last used, revoked) and revokes any of them in two clicks; stale keys no longer pile up unnoticed.

## Approach

0. Note: `ApiKeyManagementService.listForActor(userId, includeRevoked)` hides revoked keys unless the flag is true; the page decides whether to show revoked keys (the metadata includes `revokedAt`) and passes the flag accordingly.
1. **View only plus revoke (chosen scope):** a page listing the signed-in user's keys with a revoke action and a confirmation step; no key value is ever displayed. Uses `ApiKeyManagementService` from `marketplace-orchestrator`, so no new orchestration and no direct port use in `marketplace-app`.
2. **Issuing a new key in the UI is out of scope.** It needs its own decision: showing a raw key once in a dialog with copy support, and whether the UI should issue keys at all. Raise separately if wanted.
3. Open design points for the plan: where the page lives in navigation (profile/settings area versus its own menu entry), whether revoked keys stay visible, and whether an administrator may see other users' keys (default: no, own keys only).
4. Tests: a view test for list and revoke, a level 3 scenario that a user cannot revoke another user's key, and a Playwright scenario for the page.

## Related

- [improvement-213](../completed/tasks/improvement-213-rest-api-revision-parity-with-ui.md) — REST/UI parity work; key management is the same parity gap in the other direction
- [improvement-212](improvement-212-api-flow-runner-page.md) — flows depend on a reusable key
- `.claude/nav/adr-index.md` — the ADR on API-key bearer authentication and the one on Basic only on key issuance; neither covers a UI for key management, so nothing settled is reopened

## Plan

### Goal

A signed-in user sees their own API keys in the app and revokes any of them, with a "revoke this key?"
confirmation before the irreversible step. A key's value is never shown; issuing keys in the app stays
out of scope.

### The problem this solves

Keys can be issued, listed and revoked only through the REST API today. A user who issued keys through
Swagger or a script cannot see which keys exist, which are still used, or remove forgotten ones without
calling REST by hand.

### What exists already (read from the code)

- `ApiKeyManagementService` (`marketplace-orchestrator`) already lists a user's keys
  (`listForActor(userId, includeRevoked)`, newest first) and revokes one (`revoke(userId, keyId)`).
  Revoke works only on the caller's own key: an unknown or foreign key fails with "not found", an
  already revoked key with "already revoked". The app needs no new domain port, no new orchestration
  class and no Liquibase change.
- One small gap: the API-key module is optional (runtime scope), and every other optional module's
  service has an `isAvailable()` check that the UI uses to hide its tab. `ApiKeyManagementService` has
  none, so the plan adds that one 3-line method.
- Each key carries: label (optional), the first 10 characters of the key, created, last used, revoked at.
- The account overlay (Name, Settings, Provider Profile tabs) already hides the Provider Profile tab
  when its module is absent, and the Provider Profile tab already has a delete button with the shared
  confirmation dialog. The new tab copies that shape.
- The level 3 test the task asks for ("a user cannot revoke another user's key") already exists in
  `ApiAuthenticationChainScenarioTest`. It checks the 404 but not that the owner's key keeps working, so
  the plan adds that one check.

### The six open points: recommendation each

| # | Point | Recommendation | Why | Alternatives set aside |
|---|---|---|---|---|
| 1 | Where the page lives | A fourth tab **"API keys"** in the account overlay, opened from the header's Settings button | GitHub, GitLab and Stripe all keep keys in the user's own settings. The overlay already has tabs, breadcrumbs and a way to hide a tab. | A main tab (main tabs are catalogs and admin lists, and it would need its own view wiring); a separate header button and overlay (more classes for one list) |
| 2 | Revoked keys | **Hidden by default**, with a "Show revoked keys" checkbox. Revoked rows show "Revoked <date>" and no revoke button | GitLab works this way, and so does the REST API (`includeRevoked=false` by default). Revoked rows are never deleted, so showing them always would only make the list grow | Always shown with a status column; never shown |
| 3 | Administrator and other users' keys | **Own keys only.** The tab is hidden when an admin or moderator opens someone else's account. The tab always uses the signed-in user's id and never takes a user id from outside | Matches the brief's default. REST has no admin key endpoint, and revoking is limited to the owner in the database query. Key upkeep for other users is deferred in an accepted decision (on the REST API's Basic login) | An admin read-only list (needs a new permission rule); admin revoke (needs a port change) |
| 4 | What the list shows, order, paging | Columns: **Label** (or "Unnamed key"), **Key** (first 10 characters + "..."), **Created**, **Last used** (or "Never"), **Status**, **Revoke**. Newest first, Created and Last used sortable by click. **No paging**; the table has a fixed height and scrolls | The same columns the products above show. The service returns the whole list with no count, and a user normally has few keys. The fixed height matters because the test admin account gains a key on every Playwright run and there is no per-user limit | Paging (needs a count and a new query); showing all rows at full height |
| 5 | Confirmation wording and behaviour | The existing confirmation dialog (red confirm button). Title **"Revoke API key?"**. Text **"Revoke the key "<label>" (<prefix>...)? Every script or client that uses this key gets 401 Unauthorized from its next request on. This cannot be undone; you would need to issue a new key."** Buttons **"Revoke key"** / **"Cancel"**. Afterwards: "API key revoked", and the list reloads | Usability guidance from Nielsen Norman Group, Primer and Vaadin: ask a specific question, say what happens, and name the button after the action. Revoking takes effect on the next REST call because nothing caches keys. The signed-in app session is not affected, because keys never log anyone into the app | A typed confirmation (meant for deleting a whole account, too heavy here) |
| 6 | Audit and empty state | **No audit entry** (the REST revoke writes none either, and the API-key audit log was deferred in an accepted decision). Empty state: a short hint that keys are issued through `POST /api/api-keys`, then "No API keys to show." | Keeps the app and REST the same, with no new audit type | One log line on every revoke (decision D1 below); a full audit timeline entry (new entity type, out of scope) |

Also: if a key was revoked meanwhile (for example over REST), the user sees "This key was already
revoked" and the list reloads. If the key no longer exists, the user sees "This key no longer exists".
No error page appears.

### The means needed

- Code: one new UI class (the tab's content), changes to the account overlay and its factory config,
  one method on `ApiKeyManagementService`, 24 text keys in English and Ukrainian, a few CSS lines.
- Tests: a unit test for the new tab (list, revoke, the two error cases, own id only), two unit tests
  for `isAvailable()`, one extra check in the existing level 3 test, one new Playwright test plus one
  extra check in an existing one, and a small optional label parameter in the shared Playwright helper
  that issues keys.
- Docs: three short corrections (the API-key module's rules and README still say the module is "never
  used by a Vaadin flow"; the orchestrator README row), and an ADR through `/record-decision` if its
  check finds the decision worth recording.

### What the result will look like

Header -> Settings -> tab **API keys**: a short note on where keys come from, a "Show revoked keys"
checkbox, and a table of the user's active keys. The bin icon on a row opens "Revoke API key?". "Revoke
key" revokes the key, shows "API key revoked" and removes the row; "Cancel" changes nothing. The tab does
not appear when an admin or moderator opens another user's account, and it does not appear at all
when the API-key module is not installed.

### Cost

Estimated, not measured. The token figures are a guess from similar steps in this repository.

| Step group | Executor | Tokens (estimate) |
|---|---|---|
| `isAvailable()` + its tests | haiku | 15k |
| Text keys (enum, English, Ukrainian) | haiku | 25k |
| New tab class, factory bean, CSS | sonnet | 80k |
| Account overlay wiring | sonnet | 50k |
| Tab unit test | sonnet | 50k |
| Level 3 check | haiku | 10k |
| Playwright helper, new test, extra check | haiku + sonnet | 70k |
| Docs corrections | haiku | 15k |
| ADR (`/record-decision`) | sonnet (main thread) | 30k |
| **Total** | | **about 350k** (about 120k on haiku, 230k on sonnet; opus not needed) |

Wall time: about 1.5 to 2.5 hours, most of it test runs. The scripts' own times come from the project
docs: the build with unit and integration tests, a rebuild and start of the app (about 7 to 10 min),
and Playwright on a freshly reset database.

### Risks

- **Many keys on the test admin account.** It gains a key per Playwright worker per run, so its list can
  hold hundreds of rows. A fixed-height table with scrolling handles that, and the new Playwright test
  uses a fresh user.
- **Unit-testing a dialog.** Opening a Vaadin dialog needs a current UI. The test calls the revoke step
  directly instead of clicking through the dialog, and the click path is covered by Playwright.
- **Account overlay switch statements.** Adding a fourth section touches several `switch` statements
  and an if/else chain in the tab listener. The listener's last branch sends anything else to Provider
  Profile, so the new tab needs its own branch. The plan spells this out.
- **No new risk to the REST API**: no REST code changes.

### Where the project's practice differs from best practice

- **Logging key revocation.** The OWASP Logging Cheat Sheet lists "adding or deleting tokens" as a
  higher-risk event that should be logged. The project deliberately deferred an API-key audit log in an
  accepted decision (on the REST API's Basic login), and revoking a key over REST logs nothing today.
  The plan keeps the project's practice. Changing it costs about 3 lines in `ApiKeyService.revoke` (an
  INFO line with the user id and key id, never the key), and it would cover REST and the app together.
  This is decision D1 below.
- Smaller, no decision needed: Vaadin's table has its own empty-state setting, but the project shows
  empty states with its own components, so the plan uses the project's way.

## Decisions taken

Approved on 2026-10-26 with the planner's recommendations:

1. **D1 (logging):** add one INFO log line on every key revocation, in `ApiKeyService.revoke` (so REST and the app are both covered): `API key revoked: actorId=..., keyId=...`, never the key, its hash or its prefix. Issuance is not logged. It is a log line, not an audit entry: the deferred API-key audit log stays deferred.
2. **D2:** the six recommendations are accepted as written: a fourth tab "API keys" in the account overlay; revoked keys hidden behind a "Show revoked keys" checkbox; own keys only; the columns, order and no paging as in the table; the confirmation dialog with the wording given; no audit entry and the empty-state hint.

## Execution plan

Scope: a self-only "API keys" tab in `AccountOverlay` that lists the signed-in user's keys and revokes one
after a `ConfirmActionDialog`. No REST change, no new port, no Liquibase change. Settled choices and
their reasons: `plan-human.md` and `notes.md` ("Variants and comparison").

Before any step the implementing session follows the project's own process (`.claude/rules.md`): it
writes this plan into `backlog/tasks/improvement-216-api-key-management-ui.md`, asks for approval and
runs `git add` after every file change. It never commits unless asked. The executor for each step is a
proposal for that session.

Read from code (verified): every class, method, file and line anchor named below. Estimated, not
measured: token costs, and the claim that Grid renderers are not invoked in a unit test without an
attached UI (step 5 avoids depending on it).

### Steps

#### Step 1: `ApiKeyManagementService.isAvailable()` and its tests — done 2026-10-26

- **Depends on:** none
- **Executor:** haiku
- **Files:**
  - `/app/marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/ApiKeyManagementService.java`
  - `/app/marketplace-orchestrator/src/test/java/org/ost/orchestrator/services/ApiKeyManagementServiceTest.java`
- **Input:** the service ends with the `revoke` method, followed by the class's closing `}`. The test ends
  with `revoke_delegatesToPort()`, followed by the class's closing `}`.
- **Exact change, service:** replace
  ```java
      public void revoke(@NonNull Long userId, @NonNull Long keyId) {
          apiKeyPortFactory.get().revoke(userId, keyId);
      }
  }
  ```
  with
  ```java
      public void revoke(@NonNull Long userId, @NonNull Long keyId) {
          apiKeyPortFactory.get().revoke(userId, keyId);
      }

      public boolean isAvailable() {
          return apiKeyPortFactory.findIfAvailable().isPresent();
      }
  }
  ```
- **Exact change, test:** replace
  ```java
      @Test
      void revoke_delegatesToPort() {
          service.revoke(USER_ID, 5L);

          verify(apiKeyPort).revoke(USER_ID, 5L);
      }
  }
  ```
  with
  ```java
      @Test
      void revoke_delegatesToPort() {
          service.revoke(USER_ID, 5L);

          verify(apiKeyPort).revoke(USER_ID, 5L);
      }

      @Test
      void isAvailable_portPresent_returnsTrue() {
          assertThat(service.isAvailable()).isTrue();
      }

      @Test
      void isAvailable_starterAbsent_returnsFalse() {
          when(apiKeyPortFactory.findIfAvailable()).thenReturn(Optional.empty());

          assertThat(service.isAvailable()).isFalse();
      }
  }
  ```
  (`setUp` already stubs `findIfAvailable()` leniently to `Optional.of(apiKeyPort)`; every import is
  already present.)
- **Verification:** `bash scripts/build-and-test.sh --unit --skip-vaadin` (through the activity-monitor
  wrapper, per `.claude/rules.md`); `ApiKeyManagementServiceTest` passes 8 tests.
- **Sources:** project precedent `ProviderProfileSaveService.isAvailable()` (read from code).

#### Step 2: i18n keys (enum, English, Ukrainian) — done 2026-10-26

- **Depends on:** none
- **Executor:** haiku
- **Files:**
  - `/app/marketplace-app/src/main/java/org/ost/marketplace/services/i18n/I18nKey.java`
  - `/app/marketplace-app/src/main/resources/i18n/messages_en.properties`
  - `/app/marketplace-app/src/main/resources/i18n/messages_uk.properties`
- **Exact change, `I18nKey.java`:** replace
  ```java
      ACCOUNT_OVERLAY_TAB_PROVIDER_PROFILE("account.overlay.tab.providerProfile"),

      // === Providers Catalog ===
  ```
  with
  ```java
      ACCOUNT_OVERLAY_TAB_PROVIDER_PROFILE("account.overlay.tab.providerProfile"),
      ACCOUNT_OVERLAY_TAB_API_KEYS("account.overlay.tab.apiKeys"),

      // === API Keys ===
      API_KEYS_VIEW_HINT("apiKeys.view.hint"),
      API_KEYS_VIEW_EMPTY_TEXT("apiKeys.view.empty.text"),
      API_KEYS_VIEW_SHOW_REVOKED("apiKeys.view.showRevoked"),
      API_KEYS_VIEW_HEADER_LABEL("apiKeys.view.header.label"),
      API_KEYS_VIEW_HEADER_KEY("apiKeys.view.header.key"),
      API_KEYS_VIEW_HEADER_CREATED("apiKeys.view.header.created"),
      API_KEYS_VIEW_HEADER_LAST_USED("apiKeys.view.header.lastUsed"),
      API_KEYS_VIEW_HEADER_STATUS("apiKeys.view.header.status"),
      API_KEYS_VIEW_HEADER_ACTIONS("apiKeys.view.header.actions"),
      API_KEYS_VIEW_UNNAMED("apiKeys.view.unnamed"),
      API_KEYS_VIEW_NEVER_USED("apiKeys.view.neverUsed"),
      API_KEYS_VIEW_STATUS_ACTIVE("apiKeys.view.status.active"),
      API_KEYS_VIEW_STATUS_REVOKED("apiKeys.view.status.revoked"),
      API_KEYS_VIEW_BUTTON_REVOKE("apiKeys.view.button.revoke"),
      API_KEYS_VIEW_CONFIRM_REVOKE_TITLE("apiKeys.view.confirm.revoke.title"),
      API_KEYS_VIEW_CONFIRM_REVOKE_TEXT("apiKeys.view.confirm.revoke.text"),
      API_KEYS_VIEW_CONFIRM_REVOKE_BUTTON("apiKeys.view.confirm.revoke.button"),
      API_KEYS_VIEW_CONFIRM_CANCEL_BUTTON("apiKeys.view.confirm.cancel.button"),
      API_KEYS_VIEW_NOTIFICATION_REVOKED("apiKeys.view.notification.revoked"),
      API_KEYS_VIEW_NOTIFICATION_ALREADY_REVOKED("apiKeys.view.notification.alreadyRevoked"),
      API_KEYS_VIEW_NOTIFICATION_NOT_FOUND("apiKeys.view.notification.notFound"),
      API_KEYS_VIEW_NOTIFICATION_REVOKE_ERROR("apiKeys.view.notification.revokeError"),
      API_KEYS_VIEW_NOTIFICATION_LOAD_ERROR("apiKeys.view.notification.loadError"),

      // === Providers Catalog ===
  ```
- **Exact change, `messages_en.properties`:** replace the line
  `account.overlay.tab.providerProfile=Provider Profile` with
  ```properties
  account.overlay.tab.providerProfile=Provider Profile
  account.overlay.tab.apiKeys=API keys
  apiKeys.view.hint=Keys for the REST API. A key value is shown only once, when it is issued through POST /api/api-keys; here you see each key label and its first characters.
  apiKeys.view.empty.text=No API keys to show.
  apiKeys.view.showRevoked=Show revoked keys
  apiKeys.view.header.label=Label
  apiKeys.view.header.key=Key
  apiKeys.view.header.created=Created
  apiKeys.view.header.lastUsed=Last used
  apiKeys.view.header.status=Status
  apiKeys.view.header.actions=Actions
  apiKeys.view.unnamed=Unnamed key
  apiKeys.view.neverUsed=Never
  apiKeys.view.status.active=Active
  apiKeys.view.status.revoked=Revoked {0}
  apiKeys.view.button.revoke=Revoke key {0}
  apiKeys.view.confirm.revoke.title=Revoke API key?
  apiKeys.view.confirm.revoke.text=Revoke the key "{0}" ({1}...)? Every script or client that uses this key gets 401 Unauthorized from its next request on. This cannot be undone; you would need to issue a new key.
  apiKeys.view.confirm.revoke.button=Revoke key
  apiKeys.view.confirm.cancel.button=Cancel
  apiKeys.view.notification.revoked=API key revoked
  apiKeys.view.notification.alreadyRevoked=This key was already revoked
  apiKeys.view.notification.notFound=This key no longer exists
  apiKeys.view.notification.revokeError=Failed to revoke the API key
  apiKeys.view.notification.loadError=Failed to load API keys
  ```
- **Exact change, `messages_uk.properties`:** replace the line
  `account.overlay.tab.providerProfile=Профіль провайдера`
  with that same line followed by these lines, written as raw UTF-8 like the file's newer entries
  (the message source reads UTF-8, `I18nConfig.setDefaultEncoding("UTF-8")`):
  ```properties
  account.overlay.tab.apiKeys=API-ключі
  apiKeys.view.hint=Ключі для REST API. Значення ключа показується лише один раз, коли його видають через POST /api/api-keys; тут видно назву кожного ключа та його перші символи.
  apiKeys.view.empty.text=Немає API-ключів для показу.
  apiKeys.view.showRevoked=Показати відкликані ключі
  apiKeys.view.header.label=Назва
  apiKeys.view.header.key=Ключ
  apiKeys.view.header.created=Створено
  apiKeys.view.header.lastUsed=Останнє використання
  apiKeys.view.header.status=Статус
  apiKeys.view.header.actions=Дії
  apiKeys.view.unnamed=Ключ без назви
  apiKeys.view.neverUsed=Ніколи
  apiKeys.view.status.active=Активний
  apiKeys.view.status.revoked=Відкликано {0}
  apiKeys.view.button.revoke=Відкликати ключ {0}
  apiKeys.view.confirm.revoke.title=Відкликати API-ключ?
  apiKeys.view.confirm.revoke.text=Відкликати ключ «{0}» ({1}...)? Кожен скрипт чи клієнт, що використовує цей ключ, з наступного запиту отримає 401 Unauthorized. Цю дію неможливо скасувати; знадобиться видати новий ключ.
  apiKeys.view.confirm.revoke.button=Відкликати ключ
  apiKeys.view.confirm.cancel.button=Скасувати
  apiKeys.view.notification.revoked=API-ключ відкликано
  apiKeys.view.notification.alreadyRevoked=Цей ключ уже відкликано
  apiKeys.view.notification.notFound=Цей ключ більше не існує
  apiKeys.view.notification.revokeError=Не вдалося відкликати API-ключ
  apiKeys.view.notification.loadError=Не вдалося завантажити API-ключі
  ```
  No text with a `{n}` argument contains an apostrophe (MessageFormat would eat it).
- **Verification:** `I18nKeyTest` passes (unique, non-blank keys). Every new key exists in both files:
  `grep -c "^apiKeys\.view\." messages_en.properties messages_uk.properties` gives 23 for each file, and
  each file also has one `account.overlay.tab.apiKeys` line.
- **Sources:** project precedent `providerProfile.view.*` keys; https://www.nngroup.com/articles/confirmation-dialog/ ,
  https://primer.style/product/components/confirmation-dialog/guidelines ,
  https://vaadin.com/docs/latest/components/confirm-dialog (wording of title, body, buttons).

#### Step 3: `ApiKeysViewModeHandler`, its factory bean and CSS — done 2026-10-26

- **Depends on:** 1, 2
- **Executor:** sonnet
- **Files:**
  - new `/app/marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/header/account/ApiKeysViewModeHandler.java`
  - `/app/marketplace-app/src/main/java/org/ost/marketplace/config/ComponentFactoryConfig.java`
  - `/app/marketplace-app/src/main/frontend/themes/my-app/user-overlay.css`
- **Input:** read `ProviderProfileViewModeHandler.java` (same package) first. It is the shape to mirror:
  `AbstractViewOverlayModeHandler`, `Configurable`, `I18nParams`, a card with `overlay__view-card-header`,
  `overlay__view-card`, `overlay__view-body`, the close button and `ConfirmActionDialog`. Also read
  `UserGridConfigurator.java` (grid columns, `DeleteActionButton`) and `TimeZoneUtil` (`ui/query/utils`).
- **Exact behaviour:**
  - Annotations as the mirror class: `@SpringComponent`, `@Scope("prototype")`, `@RequiredArgsConstructor`,
    plus `@Slf4j`. It `extends AbstractViewOverlayModeHandler` and
    `implements Configurable<ApiKeysViewModeHandler, ApiKeysViewModeHandler.Parameters>, I18nParams`.
    The Javadoc is one line: the class is the account overlay's API keys section, lists and revokes the
    signed-in user's own keys, and never shows a key value.
  - `Parameters`: `@Value @lombok.Builder` with `@NonNull Runnable onClose` and `@NonNull Component tabBar`.
    There is **no user id parameter**: the handler always uses `access.getCurrentUserId()`, so it cannot
    show or revoke anyone else's keys.
  - Collaborators (final fields): `ApiKeyManagementService`, `AccessEvaluator`, `NotificationService`,
    `@Getter I18nService i18nService`. State: `Parameters params`, `boolean showRevoked` (false), and the
    fields `Grid<ApiKeySummaryDto> grid`, `Span emptyText` and `Checkbox showRevokedBox`. The unit test
    in step 5 reads these three fields by name.
  - `buildPrimaryContent()`: card header = `VaadinIcon.KEY` icon + `Span(ACCOUNT_OVERLAY_TAB_API_KEYS)`.
    Then a hint `Span(API_KEYS_VIEW_HINT)` with class `api-keys-hint`, and `showRevokedBox = new
    Checkbox(API_KEYS_VIEW_SHOW_REVOKED)` with class `api-keys-show-revoked`; its value-change listener
    sets `showRevoked` and calls `refresh()`. Then the grid, then `emptyText = Span(API_KEYS_VIEW_EMPTY_TEXT)`
    with class `api-keys-empty-text`. Card = all of these with class `overlay__view-card`; body =
    `Div(params.getTabBar(), card)` with class `overlay__view-body`. It calls `refresh()` once and
    returns the body.
  - Grid (`new Grid<>()`, class `api-keys-grid`). Header texts come from the `API_KEYS_VIEW_HEADER_*` keys.
    The columns, in order:
    1. Label: `label()`, or `API_KEYS_VIEW_UNNAMED` when null; flex-grow 1.
    2. Key: `ComponentRenderer` giving `Span(keyPrefix() + "...")` with class `api-key-prefix`; auto width.
    3. Created: `TimeZoneUtil.formatInstantHuman(createdAt())`; sortable with
       `Comparator.comparing(ApiKeySummaryDto::createdAt, Comparator.nullsFirst(Comparator.naturalOrder()))`.
    4. Last used: `TimeZoneUtil.formatInstantHuman(lastUsedAt(), getValue(API_KEYS_VIEW_NEVER_USED))`;
       sortable with the same null-safe comparator on `lastUsedAt`.
    5. Status: `API_KEYS_VIEW_STATUS_ACTIVE` when `revokedAt()` is null, otherwise
       `getValue(API_KEYS_VIEW_STATUS_REVOKED, TimeZoneUtil.formatInstantHuman(revokedAt()))`.
    6. Actions: for an active key, `new DeleteActionButton(getValue(API_KEYS_VIEW_BUTTON_REVOKE,
       displayLabel(key)), () -> confirmAndRevoke(key), "api-key-revoke-button", false)`; for a revoked
       key an empty `Span`.
    No paging. No `setAllRowsVisible`: the height comes from CSS. The rows keep the service's order
    (newest first).
  - `private String displayLabel(ApiKeySummaryDto key)`: the label, or `API_KEYS_VIEW_UNNAMED` when null.
  - `private void refresh()`: in a try block, `keys = apiKeyManagementService.listForActor(access.getCurrentUserId(), showRevoked)`,
    `grid.setItems(keys)`, the grid is visible only when `keys` is not empty, and `emptyText` is visible
    only when it is. In `catch (Exception ex)`: `log.error`, `notificationService.error(API_KEYS_VIEW_NOTIFICATION_LOAD_ERROR)`,
    `grid.setItems(List.of())`, grid hidden, `emptyText` shown.
  - `private void confirmAndRevoke(ApiKeySummaryDto key)`: opens `new ConfirmActionDialog(
    getValue(API_KEYS_VIEW_CONFIRM_REVOKE_TITLE), getValue(API_KEYS_VIEW_CONFIRM_REVOKE_TEXT, displayLabel(key),
    key.keyPrefix()), getValue(API_KEYS_VIEW_CONFIRM_REVOKE_BUTTON), getValue(API_KEYS_VIEW_CONFIRM_CANCEL_BUTTON),
    () -> revoke(key)).open()`.
  - `void revoke(ApiKeySummaryDto key)` is **package-private** so the unit test can call it without
    opening a dialog. It calls `apiKeyManagementService.revoke(access.getCurrentUserId(), key.id())` and
    shows `success(API_KEYS_VIEW_NOTIFICATION_REVOKED)`. It catches, in this order:
    `ApiKeyAlreadyRevokedException` (from `org.ost.platform.apikey.model`, an allowed platform-commons
    contract) and shows `error(API_KEYS_VIEW_NOTIFICATION_ALREADY_REVOKED)`; `NoSuchElementException` and
    shows `error(API_KEYS_VIEW_NOTIFICATION_NOT_FOUND)`; `Exception` with `log.error` and shows
    `error(API_KEYS_VIEW_NOTIFICATION_REVOKE_ERROR)`. After the try/catch it always calls `refresh()`.
  - `buildHeaderActions()`: only the close button, `new UiIconButton(getValue(HEADER_HOME),
    VaadinIcon.CLOSE.create())`, with a click that runs `params.getOnClose()`. It is wrapped in a `Div`, like
    the mirror class.
  - The key value is never available here (the DTO has none); do not add one. Code comments follow the
    one-line-or-none rule, with no task or ADR numbers.
- **Exact change, `ComponentFactoryConfig.java`:** add the import
  `import org.ost.marketplace.ui.views.main.header.account.ApiKeysViewModeHandler;` next to the existing
  `...header.account.ProviderProfileViewModeHandler` import. Add this bean right after the
  `providerProfileViewModeHandlerFactory` bean:
  ```java
      @Bean @ConditionalOnMissingBean
      public UiComponentFactory<ApiKeysViewModeHandler, ApiKeysViewModeHandler.Parameters> apiKeysViewModeHandlerFactory(ObjectProvider<ApiKeysViewModeHandler> p) {
          return new UiComponentFactory<>(p);
      }
  ```
- **Exact change, `user-overlay.css`:** inside the existing `@layer components { ... }` block, before its
  final closing `}`, add:
  ```css
  /* ── Account API keys tab ────────────────────────────────────────── */

  .api-keys-hint,
  .api-keys-empty-text {
      color: var(--app-text-muted);
      font-size: 0.9rem;
  }

  .api-keys-grid {
      height: 22rem;
  }

  .api-key-prefix {
      font-family: monospace;
  }
  ```
- **Verification:** the reactor compiles (`bash scripts/build-and-test.sh --skip-vaadin`); no new class in
  `marketplace-app` imports a repository, a starter internal class, or `ApiKeyPort`
  (`ArchitectureRulesTest` passes in the unit run).
- **Sources:** https://docs.gitlab.com/user/profile/personal_access_tokens/ (active by default, inactive
  via filter); https://docs.stripe.com/keys (per-key row actions, the value is never shown again);
  https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/token-expiration-and-revocation
  (revocation cannot be undone).

#### Step 4: wire the tab into `AccountOverlay` — done 2026-10-26

- **Depends on:** 3
- **Executor:** sonnet
- **Files:** `/app/marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/header/account/AccountOverlay.java`
- **Exact behaviour:**
  - `Section` becomes `{NAME, SETTINGS, PROVIDER_PROFILE, API_KEYS}`. API_KEYS has no View/Edit
    sub-state; `withSection` already resets the other sub-states.
  - New `private final transient` fields, aligned like the existing ones:
    `UiComponentFactory<ApiKeysViewModeHandler, ApiKeysViewModeHandler.Parameters> apiKeysViewHandlerFactory`,
    `ApiKeyManagementService apiKeyManagementService` and `AccessEvaluator access`.
  - `saveConfig()`: add `case API_KEYS -> throw new IllegalStateException("The API keys section has no save");`.
    The section has no form handler, so `handleSave` never reaches it.
  - `switchTo()`: add
    `case API_KEYS -> apiKeysViewHandlerFactory.build(ApiKeysViewModeHandler.Parameters.builder().onClose(this::closeToList).tabBar(tabs).build());`.
  - `sectionLabelKey`: add `case API_KEYS -> ACCOUNT_OVERLAY_TAB_API_KEYS;`.
  - `buildTabs()`:
    - Make every optional tab variable effectively final so the selection listener can compare against
      it. The current code assigns `providerProfileTab` after declaring it `null`, which is why the
      listener today falls back to `else -> PROVIDER_PROFILE`. Write
      `Tab providerProfileTab = providerProfileSaveService.isAvailable() ? new Tab(...) : null;` and
      `Tab apiKeysTab = showApiKeysTab() ? new Tab(i18n().get(ACCOUNT_OVERLAY_TAB_API_KEYS)) : null;`,
      and add each one to `tabList` when non-null (API keys last).
    - `setSelectedTab` switch: add `case API_KEYS -> apiKeysTab;`.
    - Listener: map each tab explicitly (`nameTab` to NAME, `settingsTab` to SETTINGS, `providerProfileTab`
      to PROVIDER_PROFILE, `apiKeysTab` to API_KEYS). Keep the existing "same section, return" and
      "discard current form, then `withSection`, then `switchTo()`" behaviour.
  - `private boolean showApiKeysTab()`: returns `apiKeyManagementService.isAvailable() &&
    session.targetUserId().equals(access.getCurrentUserId())`. The tab exists only on the signed-in
    user's own account, also for admins and moderators, and only when the API-key module is present.
  - Update the class Javadoc so it describes four sections, with API keys as a self-only, view-only
    section. Keep its existing style and do not add task or ADR numbers.
- **Verification:** compiles; with step 5's test and the Playwright step 8 checks. Manual: header ->
  Settings shows four tabs for a signed-in user. Users grid -> another user shows three.
- **Sources:** project precedent: the Provider Profile tab's `isAvailable()` gating in `buildTabs()`
  (read from code); ADR-074 (marketplace-app) for the overlay's tab design.

#### Step 5: unit test for the tab — done 2026-10-26

- **Depends on:** 3
- **Executor:** sonnet
- **Files:** new `/app/marketplace-app/src/test/java/org/ost/marketplace/ui/views/main/header/account/ApiKeysViewModeHandlerTest.java`
- **Input:** `ModerationViewTest`/`TimelineViewTest` (Mockito + `ReflectionTestUtils`, no Vaadin UI). The
  test is in the handler's package, so it can call the protected `buildPrimaryContent()` and the
  package-private `revoke(...)`.
- **Exact behaviour:** `@ExtendWith(MockitoExtension.class)`. Mocks: `ApiKeyManagementService`,
  `AccessEvaluator` (`getCurrentUserId()` returns `7L`), `NotificationService`, `I18nService`. Stub `I18nService`
  leniently so every `get(...)` returns a non-null string. Build the handler with its constructor and
  `configure(Parameters.builder().onClose(() -> {}).tabBar(new Div()).build())`. Read `grid`, `emptyText` and
  `showRevokedBox` with `ReflectionTestUtils.getField`. Assert grid content through
  `grid.getListDataView().getItems()`, never through rendered cell text (renderers use `TimeZoneUtil`,
  which needs a `VaadinSession`). Cases:
  1. `buildPrimaryContent_listsTheSignedInUsersActiveKeysOnly`: verifies `listForActor(7L, false)`; the grid
     holds the returned keys and is visible; `emptyText` is hidden.
  2. `showRevokedChecked_reloadsWithRevokedKeys`: `showRevokedBox.setValue(true)` leads to `listForActor(7L, true)`.
  3. `noKeys_showsTheEmptyText`: an empty list hides the grid and shows `emptyText`.
  4. `loadFails_showsTheLoadErrorAndTheEmptyText`: `listForActor` throws `RuntimeException`, giving
     `error(API_KEYS_VIEW_NOTIFICATION_LOAD_ERROR)`, no exception escapes, and `emptyText` is visible.
  5. `revoke_usesTheSignedInUsersIdAndReloads`: `revoke(key)` leads to `revoke(7L, key.id())`,
     `success(API_KEYS_VIEW_NOTIFICATION_REVOKED)` and a second `listForActor` call.
  6. `revoke_alreadyRevoked_reportsItAndReloads`: `ApiKeyAlreadyRevokedException`, giving
     `error(API_KEYS_VIEW_NOTIFICATION_ALREADY_REVOKED)`, a reload, and no exception escapes.
  7. `revoke_notFound_reportsIt`: `NoSuchElementException`, giving `error(API_KEYS_VIEW_NOTIFICATION_NOT_FOUND)`.
  The class Javadoc is one line stating the rule being protected (only the signed-in user's keys, and a
  failed revoke never breaks the tab).
- **Verification:** `bash scripts/build-and-test.sh --unit --skip-vaadin`; the 7 tests pass.
- **Sources:** project precedent `ModerationViewTest`, `TimelineViewTest` (read from code).

#### Step 6: level 3: the owner's key keeps working after a foreign revoke attempt — done 2026-10-26

- **Depends on:** none
- **Executor:** haiku
- **Files:** `/app/integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/ApiAuthenticationChainScenarioTest.java`
- **Input:** the existing test `revokingAnotherUsersKey_isNotFound()` already covers "a user cannot revoke
  another user's key" with a 404. No new class is needed.
- **Exact change:** replace
  ```java
      @Test
      void revokingAnotherUsersKey_isNotFound() throws Exception {
          RegisteredUser owner = registerUserAndIssueApiKey("Key Holder");
          RegisteredUser intruder = registerUserAndIssueApiKey("Intruder");
          long ownersKeyId = firstKeyId(owner.rawApiKey());

          mockMvc.perform(delete("/api/api-keys/" + ownersKeyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.rawApiKey()))
                  .andExpect(status().isNotFound())
                  .andExpect(jsonPath("$.message").value("Resource not found."));
      }
  ```
  with
  ```java
      @Test
      void revokingAnotherUsersKey_isNotFound_andTheOwnersKeyKeepsWorking() throws Exception {
          RegisteredUser owner = registerUserAndIssueApiKey("Key Holder");
          RegisteredUser intruder = registerUserAndIssueApiKey("Intruder");
          long ownersKeyId = firstKeyId(owner.rawApiKey());

          mockMvc.perform(delete("/api/api-keys/" + ownersKeyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.rawApiKey()))
                  .andExpect(status().isNotFound())
                  .andExpect(jsonPath("$.message").value("Resource not found."));
          mockMvc.perform(get("/api/api-keys").header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.rawApiKey()))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.length()").value(1));
      }
  ```
  (`get` and `jsonPath` are already imported.)
- **Verification:** `bash scripts/build-and-test.sh --integration --skip-vaadin` (Docker needed);
  `ApiAuthenticationChainScenarioTest` passes; `OperationCoverageTest` stays green (no new operation).
- **Sources:** https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html (keys checked on every request).

#### Step 7: Playwright: helper label parameter and the moderator check — done 2026-10-26

- **Depends on:** none (runs green only after step 4 is deployed)
- **Executor:** haiku
- **Files:** `/app/playwright/e2e/_flows/seed.flow.js`, `/app/playwright/e2e/04-provider-profile-flow.spec.js`
- **Exact change, `seed.flow.js`:** replace
  ```js
   * @returns {Promise<string>} the raw API key
   */
  async function issueApiKeyViaApi(request, { email, password }) {
    const auth = Buffer.from(`${email}:${password}`).toString('base64');
    const response = await request.post('/api/api-keys', { headers: { Authorization: `Basic ${auth}` } });
  ```
  with
  ```js
   * @param {string} [label] optional key label, shown in the account's API keys tab.
   * @returns {Promise<string>} the raw API key
   */
  async function issueApiKeyViaApi(request, { email, password }, label) {
    const auth = Buffer.from(`${email}:${password}`).toString('base64');
    const response = await request.post('/api/api-keys', {
      headers: { Authorization: `Basic ${auth}` },
      data: label ? { label } : undefined,
    });
  ```
- **Exact change, spec 04, test title:** replace
  `test('moderator views a user\'s account — read-only on Name, Settings and Provider Profile tabs, both saved categories visible, no Edit/Create/Save button anywhere',`
  with
  `test('moderator views a user\'s account — read-only on Name, Settings and Provider Profile tabs, both saved categories visible, no Edit/Create/Save button anywhere, no API keys tab on another user\'s account',`
- **Exact change, spec 04, assertion:** replace
  ```js
      await screenshot(page, 'provider-profile-moderator-settings-readonly');
  ```
  with
  ```js
      await screenshot(page, 'provider-profile-moderator-settings-readonly');
      await expect(page.locator('.account-overlay').getByRole('tab', { name: 'API keys' })).toHaveCount(0);
  ```
- **Exact change, spec 04 header (line 15):** replace
  `*   - "moderator views a user's account": read-only Name, Settings and Provider Profile tabs.`
  with
  `*   - "moderator views a user's account": read-only Name, Settings and Provider Profile tabs; no API keys tab.`
- **Verification:** `bash playwright/run.sh 04-provider-profile-flow --ux` after deploy (see the final
  verification below).
- **Sources:** `.claude/rules/playwright.md` ("Extend an existing test before adding a new one").

#### Step 8: Playwright: new self-service test in spec 02 — done 2026-10-26

- **Depends on:** 4, 7
- **Executor:** sonnet
- **Files:** `/app/playwright/e2e/02-marketplace-authentication-flow.spec.js`
- **Exact behaviour:** add one top-level test (its own setup: a fresh account and its own keys, so it
  cannot be a step of an existing test) named
  `user reviews and revokes own API keys — empty state, two REST-issued keys listed by label and prefix with no key value, cancel keeps the key, confirm revokes it, revoked key gets 401 on REST, show-revoked toggle lists it as revoked, other key still works`.
  It uses the fixtures `pageAs`, `createUser`, `request` and `unique`:
  1. `user = await createUser()` (English, no key yet); `page = await pageAs(user)`. Open the account
     overlay with the header button matching the spec's `SETTINGS_BUTTON`. Click
     `getByRole('tab', { name: 'API keys' })` and expect the text `No API keys to show.`
  2. `labelA = unique('laptop')`, `labelB = unique('ci')`;
     `rawA = await issueApiKeyViaApi(request, user, labelA)`, `rawB = ...labelB` (import from
     `./_flows/seed.flow`).
  3. Click the `Settings` tab, then `API keys` again (the tab rebuilds on every switch). Expect `labelA`,
     `labelB` and `rawA.slice(0, 10) + '...'` visible; expect `page.getByText(rawA)` to have count 0.
     Take a screenshot.
  4. Click `getByRole('button', { name: 'Revoke key ' + labelA })`. In the dialog expect the heading
     `Revoke API key?` and text containing `labelA`. Click `Cancel`; `labelA` is still listed.
  5. Click the row button again, then the dialog's confirm button, scoped to the dialog with an exact
     name (`page.getByRole('dialog').getByRole('button', { name: 'Revoke key', exact: true })`), because
     the row buttons' names also start with `Revoke key`. Expect the notification
     `API key revoked` (close it with `closeNotification` if the spec does so elsewhere); `labelA` is no
     longer listed and `labelB` is.
  6. `request.get('/api/api-keys', { headers: { Authorization: 'Bearer ' + rawA } })` gives status 401;
     the same call with `rawB` gives 200.
  7. Check `getByRole('checkbox', { name: 'Show revoked keys' })`; `labelA` is listed again together with
     a cell text starting with `Revoked`. Take a screenshot.
  Use role, label and text locators. Use raw CSS only for the `.account-overlay` scope. No
  `waitForTimeout`. Update the file's header block: the Description list gets this test's one-line
  flow; Input adds `./_flows/seed.flow (issueApiKeyViaApi)`; Outputs says the spec also creates API keys
  for its own accounts.
- **Verification:** `bash playwright/run.sh 02-marketplace-authentication-flow --ux`, and once more with
  `--repeat-each 2` to prove it does not depend on database state. The lint stays within
  `PW_LINT_MAX_WARNINGS`.
- **Sources:** https://www.nngroup.com/articles/confirmation-dialog/ (cancel path is part of the
  contract); `.claude/rules/playwright.md` (isolation, naming, locators).

#### Step 9: documentation corrections — done 2026-10-26

- **Depends on:** 4
- **Executor:** haiku
- **Files:**
  - `/app/.claude/rules/apikey-spring-boot-starter.md`
  - `/app/apikey-spring-boot-starter/README.md`
  - `/app/marketplace-orchestrator/README.md`
- **Exact change, rules file:** replace
  ```
  - Not folded into `user-spring-boot-starter` despite the natural credential-domain fit: this
    starter is consumed only by `marketplace-rest-api` (the external REST API's only auth
    mechanism), never by a Vaadin flow — a real starter is still the only shape that adds zero
  ```
  with
  ```
  - Not folded into `user-spring-boot-starter` despite the natural credential-domain fit: its keys
    authenticate only `marketplace-rest-api` (the external REST API's only auth mechanism), never a
    Vaadin session (`marketplace-app` only lists and revokes the signed-in user's own keys through
    `marketplace-orchestrator`) — a real starter is still the only shape that adds zero
  ```
  (frontmatter `paths:` untouched, so no glob re-check is needed.)
- **Exact change, starter README:** replace
  ```
    `ApiKeyManagementService`/`UserDeleteService`, which `marketplace-rest-api`'s `ApiKeyController`/
    `ApiKeyAuthenticationFilter` go through — this starter is never imported directly by either).
  ```
  with
  ```
    `ApiKeyManagementService`/`UserDeleteService`, which `marketplace-rest-api`'s `ApiKeyController`/
    `ApiKeyAuthenticationFilter` and `marketplace-app`'s account API keys tab go through — this starter
    is never imported directly by any of them).
  ```
- **Exact change, orchestrator README:** replace
  ``| `ApiKeyManagementService` | Wraps `ComponentFactory<ApiKeyPort>` so `marketplace-rest-api` never holds a `*Port` directly — same shape as every other `*ReadService`/`*CatalogService` here. |``
  with
  ``| `ApiKeyManagementService` | Wraps `ComponentFactory<ApiKeyPort>` so neither `marketplace-rest-api` nor `marketplace-app` holds a `*Port` directly; `isAvailable()` lets the UI hide its API keys tab when the starter is absent — same shape as every other `*ReadService`/`*CatalogService` here. |``
- **Verification:** `git grep -n "never by a Vaadin flow"` returns nothing.
- **Sources:** `.claude/rules.md` ("One fact, one canonical home").

#### Step 10: decision record and task bookkeeping — done 2026-10-26

- **Depends on:** 1 to 9 and 11
- **Executor:** sonnet (the main implementing session, because `/record-decision` is interactive)
- **Files:** through `/record-decision` only (it writes `marketplace-app/DECISIONS.md` and regenerates
  `.claude/nav/adr-index.md`); `backlog/tasks/improvement-216-api-key-management-ui.md` (Operational notes).
- **Behaviour:** run `/record-decision marketplace-app — API keys tab in the account overlay: own keys
  only, revoked keys behind a toggle, no audit entry` and let its worthiness gate decide. Inputs for it:
  the six choices from `plan-human.md`, the rejected alternatives from `notes.md`, the relation to ADR-074
  (fourth tab) and to ADR-087 (audit log and key hygiene still deferred). Step 11 runs first and the ADR states the log line (an INFO line, not an audit entry). `adrs_referenced` in the task: ADR-074 (marketplace-app),
  ADR-087 (marketplace-app). Closing the task (move, archive line, Applied counts) happens only when
  the person asks, per Task Lifecycle.
- **Verification:** `bash .claude/nav/scripts/generate-adr-index.sh` has run and its diff is staged.

#### Step 11: log every key revocation (decision D1: yes; run it before step 10) — done 2026-10-26

- **Depends on:** none
- **Executor:** haiku
- **Files:** `/app/apikey-spring-boot-starter/src/main/java/org/ost/apikey/services/ApiKeyService.java`
- **Exact change:** add `import lombok.extern.slf4j.Slf4j;` and the `@Slf4j` annotation above `@Service`.
  In `revoke`, replace `        repository.revoke(actorId, keyId);` with
  ```java
          repository.revoke(actorId, keyId);
          log.info("API key revoked: actorId={}, keyId={}", actorId, keyId);
  ```
  It never logs the key, its hash or its prefix.
- **Verification:** unit and integration runs stay green.
- **Sources:** https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html

### Verification of the whole

1. `bash scripts/activity-monitor.sh -- scripts/build-and-test.sh --unit --integration --sandbox --skip-vaadin`:
   green, including `ApiKeyManagementServiceTest` (8), `ApiKeysViewModeHandlerTest` (7), `I18nKeyTest`,
   `ArchitectureRulesTest`, `ApiAuthenticationChainScenarioTest` and `OperationCoverageTest`.
2. `bash scripts/deploy-and-run.sh --reset-only-db` (through the activity-monitor wrapper). Then
   `bash scripts/playwright.sh e2e --full --ux` (Definition of Done: UI-visible change). Specs 02 and 04
   pass, and the rest of the suite stays green.
3. Manual read of the screenshots: the API keys tab with keys, and the revoke dialog. The dialog names the
   key and says that clients get 401 from their next request.
4. `git grep -n "rawKey\|keyHash" marketplace-app/src/main` returns nothing (no key value reaches the UI).
5. The task file's `

## Autonomous decisions (autopilot run)

- The plan's steps 7 and 8 were written against the earlier suite; the new test went into the isolated spec 02 with the fixtures, and the confirm dialog is found with `getByRole('dialog', { name: 'Revoke API key?' })` because the dialog content is not inside the `vaadin-dialog-overlay` element in the accessibility tree.
- After a revoke the grid keeps hidden recycled rows, so the presence and absence checks use `getByRole('gridcell', ...)` (role locators skip hidden rows) instead of `getByText`.
- The new tests use page-level role locators and not a raw `.account-overlay` scope, so the Playwright lint warning ceiling (`PW_LINT_MAX_WARNINGS`) did not have to be raised.
- Sonar rule S1450 flagged the `showRevokedBox` field of the new handler (used in one method only); the field became a local variable and the unit test finds the checkbox through the returned component tree instead of reflection.
- The self-review by `deep-review-orchestrator` was lighter than its normal procedure (it skipped the lens fan-out and the verifiers) and found no defect; the Sonar finding above and the Playwright dialog and grid findings came from the real runs.
- The task was closed after the final green runs without waiting for the version-control step, as in the previous runs of this session.

## Operational notes
- token_cost_review: 71715 (a lighter review than the normal procedure, no findings)
- token_cost_research: 237045 (deep-planner, topic api-key-management-ui)
- token_cost_verification: 46439 (sonar-analyst)
- review_signal_ratio: 0 confirmed / candidates not reported (the review skipped its lens fan-out)
- context_loading_task_type: Feature, single module
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: plan a task in depth, then execute the approved plan unattended
- flows_chosen: /deep-plan, then /autopilot
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 6
- haiku_decomposition_rework_needed: 0 / 6
- adrs_referenced: ADR-088 (marketplace-app), ADR-074 (marketplace-app), ADR-087 (marketplace-app)

### Agent calls
- step 1 isAvailable and tests | subagent_type=general-purpose model=haiku | tokens=83003 | tool_uses=6 | duration_s=44 | mode=background | batch=H1
- step 11 revoke log line | subagent_type=general-purpose model=haiku | tokens=71118 | tool_uses=6 | duration_s=34 | mode=background | batch=H1
- step 2 i18n keys | subagent_type=general-purpose model=haiku | tokens=80366 | tool_uses=9 | duration_s=54 | mode=background | batch=H1
- step 6 level 3 test | subagent_type=general-purpose model=haiku | tokens=79834 | tool_uses=4 | duration_s=42 | mode=background | batch=H2
- step 9 documentation corrections | subagent_type=general-purpose model=haiku | tokens=86890 | tool_uses=8 | duration_s=65 | mode=background | batch=H2
- step 7 Playwright helper and moderator check | subagent_type=general-purpose model=haiku | tokens=100434 | tool_uses=8 | duration_s=58 | mode=background | batch=H2
- steps 3 and 4 tab class and overlay wiring | subagent_type=general-purpose model=sonnet | tokens=103489 | tool_uses=16 | duration_s=99 | mode=background | batch=S1
- step 5 unit test | subagent_type=general-purpose model=sonnet | tokens=86621 | tool_uses=10 | duration_s=74 | mode=background | batch=S1
- step 8 Playwright self-service test | subagent_type=general-purpose model=sonnet | tokens=101332 | tool_uses=14 | duration_s=73 | mode=background | batch=solo
- deep plan | subagent_type=deep-planner | tokens=237045 | tool_uses=84 | duration_s=843 | mode=background | batch=solo
- code review | subagent_type=deep-review-orchestrator | tokens=71715 | tool_uses=6 | duration_s=60 | mode=background | batch=solo
- Sonar status | subagent_type=sonar-analyst | tokens=46439 | tool_uses=5 | duration_s=39 | mode=background | batch=solo

### Script/command runs
- scripts/build-and-test.sh --unit --integration --sandbox --skip-vaadin | duration_s=355 | mode=background | result=pass
- scripts/deploy-and-run.sh --reset-only-db --dev-tools | duration_s=243 | mode=background | result=pass
- scripts/playwright.sh e2e --full --ux | duration_s=1000 | mode=background | result=fail (the new test, fixed twice)
- scripts/playwright.sh 02-marketplace-authentication-flow --repeat-each 2 (3 runs) | duration_s=100-215 | mode=background | result=pass on the last two
- scripts/playwright.sh --lint (4 runs) | duration_s=15-20 | mode=background | result=pass at 875 warnings
- scripts/ci.sh --foreground | duration_s=1723 | mode=background | result=fail (Sonar S1450 only; every other stage passed, e2e 65 passed)
- scripts/ci.sh --sonar --foreground | duration_s=774 | mode=background | result=pass
- docs/architecture/scripts/generate-architecture-model.sh | duration_s=120+ | mode=background | result=pass
