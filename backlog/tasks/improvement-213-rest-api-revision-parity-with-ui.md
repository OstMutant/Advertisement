# improvement-213: REST API revision — audit parity with the UI and list what is missing in REST

**Type:** improvement — audit that produces follow-up tasks, plus implementation of the user-domain gaps it finds (scope widened 2026-10-17)
**Module:** `marketplace-rest-api` (`api/*Controller.java`), `marketplace-app` (Vaadin views and form handlers under `ui/views/`), `marketplace-orchestrator` (the services both adapters call)
**Priority:** high — Top, first: its result decides the scope of [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md), [improvement-210](improvement-210-rest-api-feedback-and-comments.md), [improvement-212](improvement-212-api-flow-runner-page.md) and the planned MCP server
**When:** independent; do before the tasks listed above change the API

## Current state

Verified against code, 2026-10-17. `marketplace-rest-api` has five controllers: `AdvertisementApiController`, `ApiKeyController`, `ProviderProfileApiController`, `TaxonApiController`, `UserApiController`. The user endpoints are `POST /api/users` (register), `GET /api/users` (admin or moderator), `GET /api/users/{id}`, and `GET` and `PATCH /api/users/me/settings`; nothing else writes users. The Vaadin UI lets an administrator edit a user's name and role (`AccountNameFormModeHandler`), and the checks live in `marketplace-orchestrator` (`AuthorizationService`), so the logic REST would need already exists. Roles are `USER`, `ADMIN`, `MODERATOR` (`Role.java`); the first registered account becomes `ADMIN` (`UserService.register`), every later one `USER`, and no REST endpoint can change a role. Feedback and comments have a domain and orchestrator services but no REST controller (see improvement-210); advertisement media has no REST endpoint (improvement-186). No task or ADR found that records a decision to keep role changes out of REST, so the gap reads as unbuilt, not rejected.

## Why change

REST is meant to mirror what the UI can do. The gaps surface one at a time (a flow runner that cannot make a moderator, an MCP server that cannot manage users) and each is currently found by accident, with no list of what is missing or why.

## Expected benefit

One reviewed list, per domain, of what the UI can do that REST cannot, each item classified, so the follow-up REST work is planned once instead of discovered piecemeal.

## Approach

1. **Inventory (read-only):** for each domain — users, advertisements, provider profiles, taxons, feedback and comments, attachments/media, contacts, audit and history/restore, settings, API keys, moderation — list the actions the Vaadin views and form handlers offer, then the matching REST endpoint or its absence.
2. **Classify each gap** as one of: belongs in REST; UI-only by design (with the reason); security-sensitive and needs its own design (for example changing a role, removing the last administrator).
3. **Output:** a table in this file plus one `/new-task` per gap worth building, ranked in `BACKLOG.md`; existing tasks (186, 210) are linked instead of duplicated.
4. **Role-change endpoint (decided 2026-10-17):** the audit starts from the known gap that only the Vaadin form changes a role. The first endpoint to build from it is an administrator-only role change, so that an administrator can make a moderator through the API (no dev-only script is needed; see [improvement-214](../completed/tasks/improvement-214-admin-credentials-from-environment.md) for the administrator). Design points to settle: the last administrator cannot lose the role, every change is written to the audit log, and the same authorisation as the UI form is reused from `marketplace-orchestrator`.
5. **Implement everything the audit finds missing, in phases (decided 2026-10-17).** Each phase has its own unit and scenario tests, explicit `operationId`s, OpenAPI examples, a full `build-and-test.sh --unit --integration --sandbox` run and its own commit; the decision is recorded with `/record-decision`.
   - **Phase 1, users:** `role` in the `POST /api/users` response; `PUT /api/users/{id}` (name and role, `If-Match`) over `UserProfileService.save`; `DELETE /api/users/{id}` over `UserDeleteService.delete`, behaving as the UI does; `PUT /api/users/me/locale` over `UserProfileService.updateLocale`. Tests: `UserApiControllerTest` (success, 403, stale version, not found); a new Level 3 scenario (an administrator makes a moderator who can then create a taxon; an administrator cannot change their own role; delete; language); the registration tests updated for `role`.
   - **Phase 2, Basic-authentication hardening** (items 4 and 5 of [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) move here; decided 2026-10-18 because the flows and Swagger UI hit the 500 now). Plan below, under "Phase 2 plan".
   - **Phase 3, shared read rules:** move the rules that now live only in the UI into `AuthorizationService` and make the services enforce them (see "Where the read rules live"). This is the groundwork for phases 4 and 5.
   - **Phase 4, audit and history:** global timeline (administrators and moderators), per-entity activity (administrators and moderators see everything, others their own entries), and reading a snapshot so a client can restore with a normal update.
   - **Phase 4b, restore from a snapshot on the server (decided 2026-10-20):** a client sends only the snapshot id and the server returns the entity to that state, instead of the client composing a `PUT` from the snapshot (what phase 4 offers). Plan under "Phase 4b" below.
   - **Phase 5, contacts:** reveal endpoint that records the view, and the monthly view counts. Decided: contacts stay open to visitors who are not signed in, as in the UI (they are advertising); the reveal limit stays per IP, and per IP plus user when signed in; the viewer in the recorded view is null for a visitor.
   - **Phase 6, feedback and comments** (the content of [improvement-210](improvement-210-rest-api-feedback-and-comments.md)): list, aggregate, create, comment, delete comment, flag. Moderation (queue, approve, reject, confirm violation) is included now that the scope is everything; it was UI-only in improvement-210, to be confirmed when this phase starts.
   - **Phase 7, media** (the first item of [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md)): upload, list and delete advertisement media.
   - **Phase 8, loose ends (decided 2026-10-20):** revoked API keys hidden from the key list by default, the Viber link made correct, and a test class named after its file; plan under "Phase 8" below. Final verification runs are listed under "Final verification" below.
6. **Where the read rules live (decided 2026-10-17):** following the existing decision to keep authorisation in `marketplace-orchestrator`, each rule is a predicate in `AuthorizationService` written once; `AuditQueryService` and `ContactAccessService` receive the acting user and enforce it (the visibility level is derived from the role, not taken from a caller argument such as `showAll`); the UI's `AccessEvaluator` calls the same predicate to show or hide controls; REST calls the same services with the authenticated principal. Their signatures change, so every UI caller (`AuditActivityPanel`, `TimelineView`, `ContactRevealPanel`, the overlays) is updated and its tests rerun.

## Branching and commits (decided 2026-10-18)

The work runs on its own branch, `feature/improvement-213-rest-api-parity`, created from `feature/marketplace-features`. Each phase is committed separately on that branch once its tests are green; when every phase is done the branch is merged back into `feature/marketplace-features`. Commits are made only on the user's explicit request.

## Findings (2026-10-17)

Method: the operations the Vaadin views call on the orchestrator services (`marketplace-app/.../ui/views`) compared with the 25 operations of the running app's `/v3/api-docs`. Legend: yes = in REST, no = missing, partly = different mechanism.

| Domain | UI capability (call) | REST | Class |
|---|---|---|---|
| Users | list, filter, sort (`getFiltered`, `count`) | yes `GET /api/users` (admin or moderator) | — |
| Users | view one (`findById`) | yes `GET /api/users/{id}` | — |
| Users | register (`register`) | yes `POST /api/users`, response has no `role` | small fix |
| Users | edit name and role (`UserProfileService.save`) | no | build |
| Users | delete (`UserDeleteService.delete`) | no | build, needs design |
| Users | page sizes (`saveSettings`) | yes `PATCH /api/users/me/settings` | — |
| Users | language (`updateLocale`) | no, settings request has four page sizes only | small fix |
| Users | sign in and out | partly: Basic or Bearer key instead of a session | by design |
| Advertisements | list, view, create, update, delete | yes, full set | — |
| Advertisements | media upload, video link, temp uploads (`AttachmentMediaService`) | no | tracked: improvement-186 |
| Advertisements | history and restore (`AuditQueryService.getSnapshotContent`, `loadRestored`) | no | decide |
| Advertisements | contact reveal (`ContactAccessService.resolveContact`, `recordView`) | no | decide, privacy |
| Provider profiles | list, view, create, update, delete | yes, full set | — |
| Provider profiles | contact reveal and view counts (`countViewsThisMonth`) | no, REST only accepts phone, telegram, viber on write | decide, privacy |
| Provider profiles | history | no | decide |
| Taxons | list, get, create, update, soft-delete | yes, full set | — |
| Taxons | history | no | decide |
| Feedback and comments | list, aggregate, create, comment, delete comment | no | tracked: improvement-210 |
| Moderation | queue, approve, reject, confirm violation, flag | no | UI-only by design (decided in improvement-210) |
| Audit | global timeline, per-entity activity (`getTimelinePage`, `countTimeline`, `getEntityActivity`) | no | decide, who may read |
| API keys | no UI use found in the views searched | yes list, issue, revoke | REST-only |

Implementability, checked in `marketplace-orchestrator`:

| Gap | Ready service | Authorisation already in the service | REST work | Open point |
|---|---|---|---|---|
| Edit name and role | `UserProfileService.save(UserProfileDto(id, name, role, version), actingUserId)` | `requireCanEditAccount`, and `requireCanEditRole` when the role changes | `PUT /api/users/{id}` with `If-Match` (two required fields, full replace like the other PUTs) | none: `AuthorizationService.canEditRole` allows a role change only to an administrator on someone else's account, so the last administrator cannot demote themselves |
| Delete user | `UserDeleteService.delete(userId, actingUserId)` (removes profile and API keys first) | `requireCanEditAccount` (self or administrator) | `DELETE /api/users/{id}` | decided: same as the UI, self or administrator; an administrator can delete their own account, last administrator included (noted, not guarded) |
| Language | `UserProfileService.updateLocale(userId, locale)` | own account | separate endpoint `PUT /api/users/me/locale` (decided: the UI changes the language apart from the settings form) | none |
| Role in register response | the user is already read after registration (`UserDto.role`) | public endpoint | one field on `UserCreatedResponse` | none |
| Audit and history | `AuditQueryService.getEntityActivity`, `getSnapshotContent`, `getTimelinePage`, `countTimeline` | none in the services: the global timeline tab is shown only when `AccessEvaluator.canView()` (= `isPrivileged()`, administrators and moderators); entity history is filtered by the arguments the UI passes (`userId`, `showAll` = privileged), not by a check inside `AuditQueryService` | read-only endpoints; the rules move into `AuthorizationService` and the audit service enforces them (phases 3 and 4) | none |
| Restore | UI loads a snapshot into the form and saves it | the entity's own save rules | none: a client reads the snapshot and sends a normal update | confirm no dedicated endpoint is wanted |
| Contacts | `ContactAccessService.resolveContact`, `recordView`, `countViewsThisMonth` | none in the service: `resolveContact` has no check and `recordView` accepts a null viewer, so the UI reveals contacts to anonymous visitors too | reveal endpoint that records the view (phase 5) | decided: open to visitors who are not signed in, as in the UI; rate limited per IP |

Conclusion: the user-domain gaps (edit, delete, language, role in the register response) can be built now with small effort because the services and their authorisation exist, and no new last-administrator guard is needed for role changes. Audit, history and contact reveal rest on checks that live in the UI, not in the services, so their REST endpoints must re-implement those rules and are left for a separate decision.

Decisions (2026-10-17): delete behaves as in the UI; the language is a separate endpoint; contacts stay open to visitors who are not signed in; this task implements every gap found, in the phases listed in the Approach, with unit and scenario tests.

## Phase 1 — users (approved 2026-10-17, implemented and verified 2026-10-18)

**Plain language:** the REST API can only register users and read them. After this phase an
administrator can rename a user and change their role, an account owner or administrator can delete
an account, a signed-in user can change their own language, and registering tells the caller which
role they received. Everything is checked with automated tests.

**Technical layer.**

*Unit A — `marketplace-rest-api/src/main/java/org/ost/restapi/api/UserApiController.java`
(fully specified, one Haiku dispatch; the exact edits are below).*

1. Imports to add (keep the file's alphabetical order): `jakarta.validation.constraints.NotBlank`,
   `jakarta.validation.constraints.NotNull`, `jakarta.validation.constraints.Pattern`,
   `jakarta.validation.constraints.Size`, `org.ost.orchestrator.services.UserDeleteService`,
   `org.ost.platform.user.dto.UserProfileDto`, `org.ost.platform.user.model.Role`,
   `org.springframework.web.bind.annotation.DeleteMapping`,
   `org.springframework.web.bind.annotation.PutMapping`.
2. Add the field `private final UserDeleteService userDeleteService;` directly after
   `private final AuthorizationService authorizationService;` (constructor order becomes
   `UserProfileService`, `AuthorizationService`, `UserDeleteService`).
3. `register(...)`: the last line becomes
   `return new UserCreatedResponse(user.id(), user.name(), user.email(), user.role());`
4. The record becomes
   `public record UserCreatedResponse(Long id, String name, String email, Role role) {` followed by `}`
   as today.
5. `getById(...)` keeps its annotations and body check, and its return type becomes
   `ResponseEntity<UserDto>`:
   ```java
   public ResponseEntity<UserDto> getById(@AuthenticationPrincipal Long actorId, @PathVariable Long id) {
       authorizationService.requireIsPrivileged(actorId);
       UserDto user = userProfileService.findById(id).orElseThrow(NoSuchElementException::new);
       return ETagUtil.withVersion(ResponseEntity.ok(), user.version()).body(user);
   }
   ```
   plus `@ApiResponse(responseCode = "200", headers = @Header(name = "ETag", description = "Version to send back via If-Match on PUT"))`
   above `@GetMapping("/{id}")`.
6. Directly after `getById`, add the three operations:
   ```java
   @Operation(operationId = "updateUser", summary = "Update a user's name and role", description = "Full replace of name and role. A user may change their own name; only an administrator may change a role, and never their own. If-Match must carry the version from the last GET /api/users/{id} ETag.")
   @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
           {
             "name": "Max Moderator",
             "role": "MODERATOR"
           }""")))
   @PutMapping("/{id}")
   @SecurityRequirement(name = "bearerKey")
   public ResponseEntity<UserDto> update(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestBody @Valid UserUpdateRequest request,
           @Parameter(description = "Version from the last GET response's ETag") @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
       userProfileService.save(new UserProfileDto(id, request.name(), request.role(), ETagUtil.parseIfMatch(ifMatch)), actorId);
       UserDto updated = userProfileService.findById(id).orElseThrow(NoSuchElementException::new);
       return ETagUtil.withVersion(ResponseEntity.ok(), updated.version()).body(updated);
   }

   @Operation(operationId = "deleteUser", summary = "Delete a user", description = "Allowed for the account's owner or an administrator, as in the UI. The user's advertisements, provider profile and API keys are removed first.")
   @DeleteMapping("/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @SecurityRequirement(name = "bearerKey")
   public void delete(@AuthenticationPrincipal Long actorId, @PathVariable Long id) {
       userDeleteService.delete(id, actorId);
   }

   @Operation(operationId = "changeMyLocale", summary = "Change the caller's own language", description = "Supported locales: en, uk.")
   @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
           {
             "locale": "uk"
           }""")))
   @PutMapping("/me/locale")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @SecurityRequirement(name = "bearerKey")
   public void updateLocale(@AuthenticationPrincipal Long actorId, @RequestBody @Valid LocaleWriteRequest request) {
       userProfileService.updateLocale(actorId, request.locale());
   }
   ```
7. At the end of the class, next to the other records:
   ```java
   /** Caller-writable user fields -- id and version are server-managed (path variable / If-Match header). */
   public record UserUpdateRequest(@NotBlank @Size(max = SignUpDto.NAME_MAX_LENGTH) String name, @NotNull Role role) {
   }

   /** The language to switch the caller's own account to. */
   public record LocaleWriteRequest(@NotNull @Pattern(regexp = "en|uk") String locale) {
   }
   ```
8. The class Javadoc line becomes: `/** Self-service registration (public), administrator account management, and ADMIN/MODERATOR read access to the user list. */`

Behaviour that comes for free from the existing services, with the HTTP code the existing
`ApiExceptionHandler` gives it: a role change by a non-administrator, or on one's own account, is
`403`; a stale `If-Match` is `412`; an unknown id is `404`; a blank name or a missing role is `400`;
a locale other than `en` or `uk` is `400`.

*Unit B — tests (written by the main thread, judgment on assertions; not dispatched).*
- `marketplace-rest-api/src/test/java/org/ost/restapi/api/UserApiControllerTest.java`: add
  `@Mock UserDeleteService`, pass it as the third constructor argument in `setUp`, and add cases —
  register returns `role`; `GET /api/users/{id}` sets `ETag`; `PUT` success returns `200` with `ETag`
  and calls `save` with the parsed version; `PUT` when `save` throws `AccessDeniedException` is `403`;
  `PUT` when it throws `StaleWriteException` is `412`; `PUT` with a blank name is `400`; `PUT` with an
  unknown id is `404`; `DELETE` is `204` and delegates with the actor id; `DELETE` when the service
  throws `AccessDeniedException` is `403`; `PUT /api/users/me/locale` is `204` and delegates; an
  unsupported locale is `400`.
- `integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/UserRegistrationScenarioTest.java`:
  assert the first registered user's response says `ADMIN` and the second's says `USER`.
- New `integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/UserAdminScenarioTest.java`
  (same base class and annotations as `TaxonAdminScenarioTest`): an administrator makes a user a
  moderator, the moderator then creates a taxon (`201`) while a plain user still gets `403`; an
  administrator changing their own role gets `403`; a plain user changing someone else's role gets
  `403`; an account owner deletes their own account and can no longer authenticate; the locale change
  is stored (`204`) and a bad locale is `400`.

*Unit C — documentation (main thread).* `.claude/rules/marketplace-rest-api.md` ("What it owns": the
new nested records `UserUpdateRequest`, `LocaleWriteRequest` and the changed `UserCreatedResponse`) and
`marketplace-rest-api/README.md` (the user-endpoints sentence). No ADR in this phase: it adds endpoints
over already-decided authorisation; the architectural decision (read rules moving into the
orchestrator) is recorded in phase 3.

*Execution and verification.* Unit A goes to one Haiku dispatch with the exact text above, and I
verify its diff myself. Then `/review` of the diff and apply its findings, then
`bash scripts/build-and-test.sh --unit --integration --sandbox` in the background under the activity
monitor, then `/sync-docs`. The full `scripts/ci.sh` run (end-to-end, Sonar) is skipped for this
phase because nothing visible in the UI changes. The task stays open (the later phases remain); the run
ends staged and ready for "зроби коміт", with no commit made.

**Decisions recorded for this phase:** `PUT` (full replace of two fields) instead of `PATCH`;
`If-Match` optional as on the other endpoints; delete returns `204` and behaves as the UI;
supported locales hard-coded as `en|uk` in the request validation because the UI's locale list lives
in `marketplace-app` and the REST module cannot import it (same reason as `SORTABLE_FIELDS`).

## Phase 2 — Basic-authentication hardening (approved 2026-10-18, implemented and verified 2026-10-18)

**Plain language:** the API wrongly accepts a login and password on every endpoint. When one is sent to an endpoint that expects an API key, the server crashes with a 500 instead of saying "not authorised", and the browser's login prompt keeps the wrong credentials. After this phase a login and password work only for getting an API key, every other endpoint answers 401, and guessing passwords at the key endpoint is limited to 5 failed tries per 15 minutes, like the login screen.

**Technical layer.**

*Facts it rests on.* `ApiSecurityConfig` has one chain for `/api/**` with `httpBasic` on and `anyRequest().authenticated()`; Basic then yields an `AuthenticatedPrincipal` while bearer-only methods take `@AuthenticationPrincipal Long actorId`, which resolves to null (`NullPointerException`, 500, seen at `ApiKeyController.list`). The reverse also fails: a Bearer request to `POST /api/api-keys` leaves `@AuthenticationPrincipal AuthenticatedPrincipal` null. No test sends the wrong kind of credential to an endpoint (unit tests plant the principal by hand, level 3 only uses Basic for key issuance). The Vaadin application has its own chain in `marketplace-app` without an `@Order`; Spring Security 7.1.x is installed.

*Best-practice check (web search, 2026-10-18; secondary sources, none authoritative).* Several chains with narrow matchers, the specific one ordered first, is the documented way to give different endpoints different authentication; the common mistake is a broad `/api/**` chain ordered first that captures everything, which this design avoids. The search did not confirm what a `401` without a challenge header does; from general knowledge, HTTP requires a `401` to carry a `WWW-Authenticate` challenge, and browsers show their login prompt only for a Basic (or Digest) challenge, so the general chain answers `401` with `WWW-Authenticate: Bearer` instead of omitting the header (to be checked against the HTTP semantics RFC and the Spring reference). On failed-login limits, OWASP-derived sources say 3 to 5 failures is typical, that counting by address alone is bypassed by distributed attempts and counting by account alone is bypassed by spraying across accounts, and that a lockout can itself be used to lock a legitimate user out.

*Unit A — security configuration (`marketplace-rest-api/.../config/ApiSecurityConfig.java`).* Two chains instead of one:
1. `@Order(1)` key-issuance chain, matcher `POST /api/api-keys` only: `httpBasic` on, `BasicLoginRateLimitFilter` added before `BasicAuthenticationFilter`, `anyRequest().authenticated()`, csrf off, stateless, form login and logout off. The bearer filter is not in this chain, so a Bearer request here is `401` with the Basic challenge. The key endpoint is the one place a login is expected, so it keeps `WWW-Authenticate: Basic`.
2. `@Order(2)` general chain, matcher `/api/**`: `httpBasic` removed, the existing public rules unchanged (`POST /api/users`, the public `GET`s), `ApiKeyAuthenticationFilter` kept, and an entry point that answers `401` with `WWW-Authenticate: Bearer realm="api"` when no bearer header was sent and with `error="invalid_token"` added when a bearer header was present but failed (RFC 6750); there is no Basic challenge, so the browser shows no login prompt. The response that returns the raw key carries `Cache-Control: no-store`. A Basic header on these endpoints is ignored, so the request is anonymous and answered `401`.
The exact matcher factory and the filter anchor (`BasicAuthenticationFilter` is no longer in the general chain) are checked against Spring Security 7.1 while implementing.

*Unit B — new `BasicLoginRateLimitFilter` (`marketplace-rest-api/.../config/`).* A `OncePerRequestFilter`: reads the `Authorization: Basic` header, decodes the username, a `FailureRateLimiter(5, 15 minutes)` from `platform-commons`; `checkAllowed` before the chain, on `TooManyAttemptsException` writes `429` with `{"message":"Too many failed login attempts, try again later"}` itself (filters sit outside the controller advice); after the chain `recordFailure` when the response status is `401` and `clear` on a 2xx. Requests with no or malformed Basic header pass through untouched. One counter, keyed by `remoteAddr|normalised email` (trimmed, lower-cased, length-capped), the same shape as `AuthService.login`, so the UI login and the key endpoint behave alike; 5 failed tries within 15 minutes, a success clears it. The `429` carries `Retry-After` set to the window length (the limiter cannot report the time left). Decided 2026-10-18: one counter, simplicity over the three-counter variant considered earlier. Known limits, accepted: an attacker who changes address gets a fresh counter for the same account, and one address spraying many accounts gets a counter per account; web sources recommend an account-bound counter and warn that any lockout can be used to delay a victim, which here only delays key issuance, never bearer-key requests. No ready library covers failed-login throttling (Spring Security ships none; Bucket4j, Resilience4j and Guava limit request rates, not failures, and cannot know the outcome before consuming), and the existing `FailureRateLimiter` already is the in-memory failure counter, so no dependency is added.

*Unit C — tests (main thread).* Unit `BasicLoginRateLimitFilterTest`: no header passes through; five wrong tries then the sixth is `429` with `Retry-After`; success clears the counter; another email has its own counter; the email is normalised (case, spaces) before it is counted; a malformed header passes through. Level 3 `ApiAuthenticationChainScenarioTest` on the real chain: Basic to `GET /api/api-keys` is `401` with a `Bearer` challenge and no `Basic` one; Basic to `POST /api/advertisements` is `401`; Basic to the public `GET /api/advertisements` is `200`; Bearer to `POST /api/api-keys` is `401`; a valid Basic to `POST /api/api-keys` is `201`; five wrong passwords then the sixth is `429`; a different email is unaffected. The existing level 3 scenarios keep running through both chains.

*Unit D — documentation and decision.* `.claude/rules/marketplace-rest-api.md` (the config section and the key-issuance constraint, now structural) and the module README data-flow paragraph; items 4 and 5 in improvement-186 replaced by a pointer to this phase; a new decision through `/record-decision` (REST decisions live in `marketplace-app/DECISIONS.md`): Basic only on key issuance in its own chain, with login rate limiting; `adrs_referenced` of this task updated and the ADR index regenerated.

*Execution and verification.* Units A and B need judgment on the Spring Security 7.1 API, so the main thread writes them; the pointer edit in improvement-186 and the two documentation edits go to one Haiku dispatch with exact text once the code is final, and I verify its diff. Then `/review`, the build-and-test run with the unit, integration and sandbox options in the background under the activity monitor, and `/sync-docs`. The end-to-end suite is not run for this phase (nothing visible in the UI changes), but the REST seeding in the Playwright specs and the Swagger check use the changed chains, so it is run once at the end of all phases. The run ends staged on the task branch; the save into history is made only on the explicit "зроби коміт".

**Deferred, noted only (decided 2026-10-18; not part of this phase):** equalising response time for an unknown email versus a wrong password; an audit log of key issuance, revocation, failures and blocks; requiring HTTPS on the key endpoint; trusted-proxy configuration for the client address once a reverse proxy is added (`server.forward-headers-strategy=NATIVE` with Tomcat's internal-proxies list, never empty, and never reading `X-Forwarded-For` by hand); progressive lockout and extra counters; API-key hygiene (expiry, last use, per-user cap).

**Decisions recorded for this phase:** one counter, 5 failed tries per 15 minutes per address and email; `401` with a `Bearer` challenge on the general chain; the key endpoint keeps the `Basic` challenge.

## Phase 3 — shared read rules (approved 2026-10-19)

No new predicates: `AuthorizationService.canOperate(actingUserId, ownerId)` and `isPrivileged(actingUserId)` already express every UI rule (verified in code). The services take the acting user and enforce them:

- `AuditQueryService`: `getEntityActivity(entityType, entityId, actingUserId)` derives `showAll` from `isPrivileged` (the `showAll` argument is removed); `getTimelinePage` and `countTimeline` take `actingUserId` and require a privileged user (the timeline tab is shown only to administrators and moderators); `getSnapshotContent(actingUserId, entityId, snapshotId, entityType, targetClass)` lets a privileged user read any snapshot and any other user only a snapshot that appears in their own visible `getEntityActivity` list (snapshots carry no owner, and the UI shows a non-privileged user only their own entries), otherwise `AccessDeniedException`.
- `ContactAccessService.countViewsThisMonth(actingUserId, entityType, entityId, ownerId)` requires `canOperate(actingUserId, ownerId)`; `resolveContact` and `recordView` stay open to visitors.
- UI callers pass the current user: `AuditActivityPanel`, `TimelineView`, `ProviderProfileViewModeHandler`, and the five restore handlers (account, provider profile, settings, advertisement, taxon). `TimelineView` keeps its own non-privileged branch, now unreachable from the UI because the tab is hidden; noted, not removed.
- Tests: new `AuditQueryServiceTest`, extended `ContactAccessServiceTest`, UI tests updated, full `build-and-test.sh --unit --integration --sandbox`; then `/review`, `/sync-docs`, a decision record, one commit for the phase on request.

## Phase 4 — audit and history (approved and implemented 2026-10-19)

Placement follows the resource-revisions guidance (history is a sub-collection of its resource, newest first, read-only content) and the project's own decisions: filters bind onto the existing DTO with `@Valid`, pagination through `Link` and `X-Total-Count`, no envelope; restore is client-side (read the snapshot, send a normal update with `If-Match`); read rules are enforced in `AuditQueryService`, not in controllers.

- `GET /api/audit/timeline` (`getAuditTimeline`) in a new `AuditApiController`: administrators and moderators only. Filters `actorIds`, `entityTypes`, `actionTypes`, `fromDate`, `toDate` bound to `AuditTimelineFilterDto` (`@Valid`, its `@ValidRange` applies); `page`, `sort` (only `createdAt`, as the UI offers); page size from the caller's saved `timelinePageSize`; `X-Total-Count` and `Link` through `PagedResponseBuilder`.
- History of one entity, unpaged like the UI, newest first (verified: the query orders by id descending): `GET /api/advertisements/{id}/history`, `/api/provider-profiles/{id}/history`, `/api/taxons/{id}/history`, `/api/users/{id}/history` and `GET /api/users/me/settings/history`, with `operationId`s `getAdvertisementHistory`, `getProviderProfileHistory`, `getTaxonHistory`, `getUserHistory`, `getMySettingsHistory`. Each controller gets two thin methods over one shared helper in `org.ost.restapi.api.audit` (call to `AuditQueryService`, response assembly). Administrators and moderators see every entry, anyone else only the entries they authored.
- One snapshot per history entry: `…/history/{snapshotId}` on the same five resources (`operationId`s `get…HistorySnapshot`): the snapshot object and its version for a restoring update; `403` when the snapshot is not in the caller's visible activity.
- **Security chain:** `GET /api/advertisements/**`, `/api/provider-profiles/**` and `/api/taxons/**` are public in `ApiSecurityConfig`, so the new history paths would be reachable anonymously and the controller would receive no user id. The chain gets explicit rules, ahead of the public ones, that require authentication for `*/history` and `*/history/*` under those three prefixes; a scenario test proves an anonymous request gets `401` with the Bearer challenge.
- Responses are dedicated records nested in the helper's package (snapshot id, action, time, actor id, the already-enriched list of changes; the snapshot endpoint returns the snapshot object and its version), not `AuditTimelineItemDto`; list entries carry no `@type` or `schemaVersion` (the snapshot endpoint is the one exception, see below). Errors: `403` from `AccessDeniedException`, `400` for an invalid id or dates, `404` for an unknown snapshot. Restoring an advertisement's media is not covered (phase 7).
- Noted, not built: the audit data holds personal data (user email, provider contacts) with the same visibility the UI already gives, so nothing is masked; reads of audit data are not themselves audited (add to the deferred list).

Tests: `AuditApiControllerTest` and a controller test for each of the five history method pairs (MockMvc: privileged and regular caller, paging headers, validation, `403`, `404`); a Level 3 scenario (administrator reads the timeline; a regular user gets `403` on it, sees only their own entries on an entity, cannot read another user's snapshot, anonymous gets `401` on history paths; a snapshot read feeds a restoring `PUT`); full `build-and-test.sh --unit --integration --sandbox`; then `/review`, `/sync-docs`; the phase is saved into history in one step, on request only. No new decision record: the rules are already recorded.

Deferred, noted only: reads of audit data are not themselves audited; personal data in snapshots (user email, provider contacts) is shown with the same visibility as in the UI and is not masked. Accepted deviation: the snapshot response exposes the recorded snapshot object as serialized, with its @type and schemaVersion, so a client can restore with typed values. The Level 3 test context gained the audit starter and the snapshot subtype registration.

## Phase 4b — restore from a snapshot on the server (planned 2026-10-20; not started)

Why: phase 4 follows the existing restore model (read the snapshot, send a normal update), which matches the UI but makes every client rebuild the update body. The wanted behaviour is a single call with the snapshot id. The restore decision on file says restore goes TO the snapshot and that no server-side restore method exists because the UI fills a form; this phase adds one for REST and the UI is moved onto the same mapping, so the rule is written once.

- Endpoint on the same five resources: `POST /api/advertisements/{id}/history/{snapshotId}/restore` (and provider-profiles, taxons, users, `/api/users/me/settings/history/{snapshotId}/restore`), `operationId`s `restore<Resource>FromSnapshot`; the resource's current `ETag` is required in `If-Match` so a restore cannot overwrite an edit the caller has not seen; success returns the updated resource with its new `ETag`.
- Where the logic lives: a "snapshot to save DTO" mapping and a `restoreFromSnapshot` method in the orchestrator services that already own each save (`AdvertisementSaveService`, `ProviderProfileSaveService`, `UserProfileService`, `TaxonCatalogService`, settings), calling the normal save path so validation, authorization (same as an update; a role change only by an administrator) and audit capture apply unchanged. The five UI restore handlers drop their own mapping and call the same service method, then reload the form.
- Snapshot visibility reuses the phase 3 rule (a privileged user, or the caller's own visible entry); `403` otherwise, `404` for an unknown snapshot, `409`-style handling of an entity that was deleted since.
- Decisions to settle at the start of the phase: whether the audit entry is `UPDATED` or a new `RESTORED` entry (`RESTORED` exists today only for restoring a soft-deleted taxon); what a user snapshot restores (the stored save path changes name and role only, the snapshot also holds an email); the advertisement media is left unchanged until the media phase.
- Also to confirm for this phase or the next: `POST /api/taxons/{id}/restore` (restore a soft-deleted taxon, `TaxonCatalogService.restore`), a parity gap found 2026-10-20 that the findings table lacked.
- Tests: service tests per resource (mapping, authorization, stale version, deleted entity), controller tests, a Level 3 scenario per resource family (edit, restore by id, state equals the snapshot, new audit entry visible in the history), the UI restore handlers' tests updated; full `build-and-test.sh --unit --integration --sandbox`; `/review`, `/sync-docs`; the extension of the restore decision recorded through `/record-decision`; saved into history in one step on request.

## Phase 5 — contacts (approved and implemented 2026-10-20)

Decisions taken: contacts stay open to visitors who are not signed in (they are advertising); every reveal is counted, as in the UI; the reveal returns the value and a ready deep link built on the server; the reveal is a `POST` because it records a view and counts toward the limit; the limit is 30 reveals per 15 minutes per client address, or per address and user when signed in, answered with `429` and `Retry-After`.

- **Orchestrator:** a new `ContactRevealService` owns the reveal: it takes the client address, the viewer id (null for a visitor), the entity and the channel; enforces the limit (the limiter moves out of `marketplace-app`, now taking the address as a plain string so the orchestrator stays transport-agnostic); resolves the contact through `ContactAccessService.resolveContact` (an advertisement without its own contact falls back to its owner's profile); records the view; and returns the value with its deep link (Telegram `https://t.me/<value>`; Viber `viber://chat?number=<value>` taken from the UI, not confirmed by Viber's own documentation and to be checked on a device; none for a phone). `ContactRevealPanel` is moved onto this service so the rule lives once.
- **REST, one new `ContactApiController`** with `operationId`s: `GET /api/advertisements/{id}/contact` and `GET /api/provider-profiles/{id}/contact` (public, the channels that are set, without values); `POST /api/advertisements/{id}/contact/reveals` and `POST /api/provider-profiles/{id}/contact/reveals` with `{ "channel": "PHONE" }` (public; `404` when the channel or contact is absent, `429` over the limit); `GET /api/provider-profiles/{id}/contact/views` (this month's counts per channel, the profile's owner, administrators and moderators only, as in the UI).
- **Security chain:** `POST` on the two `…/contact/reveals` paths gets an explicit `permitAll`, and `GET …/contact/views` an explicit `authenticated()` ahead of the public `GET` rules (otherwise the controller receives no user id, as with history).
- **Tests:** `ContactRevealServiceTest` (limit, visitor and signed-in keys, absent contact or channel, recorded view with a null viewer, the three deep links); `ContactApiControllerTest`; a Level 3 scenario (a visitor reveals a profile phone and the owner then sees one view; the 31st reveal is `429`; a channel that is not set is `404`; the counts are `403` for another user and `401` for a visitor; an advertisement falls back to its owner's profile contact); the UI reveal tests; the two Playwright specs that exercise the reveal (`04-provider-profile-flow`, `05-marketplace-advertisement-flow`) with `--ux` because UI code is touched; full `build-and-test.sh --unit --integration --sandbox`; `/review`, `/sync-docs`; the contact-service decision extended through `/record-decision`; saved into history in one step on request.

## Phase 8 — loose ends (approved and implemented 2026-10-20)

- **Revoked keys in the key list.** `DELETE /api/api-keys/{id}` only marks a key revoked, and `GET /api/api-keys` still returned it, so a revoked key looked undeleted. The list hides revoked keys by default and `?includeRevoked=true` returns them with a non-null `revokedAt`. The filter is applied where the list is read (`ApiKeyPort.listForActor` gains the flag, with the starter, the orchestrator's `ApiKeyManagementService` and the controller passing it through), the Swagger descriptions of the list and the revoke operation are corrected, and the tests are extended (starter, service, controller, and the Level 3 key scenario: revoke, list without the key, list with `includeRevoked=true`).
- **Viber link.** Checked 2026-10-20: Viber's developer documentation has no link that opens a chat by phone number (only bot links), the help centre documents `viber.me/<digits only>` for business accounts, and the form used in practice is `viber://chat?number=%2B<digits>`. The link built today, `viber://chat?number=+380...`, leaves the plus sign raw in a query string, where it is read as a space, so it very likely does not work. The deep link is built once in `ContactRevealService`; it gets the value URL-encoded (`+` as `%2B`), with a unit test, and the UI inherits it. A check on a real device with Viber installed is still needed and is listed under "Final verification".
- **Test naming.** `ContactAccessServiceTest.java` declares the class `ContactServiceTest`. The class is renamed to match its file (the only such mismatch in the repository, scanned 2026-10-20) and any reference is updated.
- Tests, `/review`, `/sync-docs` and the save into history follow the same routine as the other phases.

## Final verification (to run when all phases are done)

Run once, after the last phase, and write the result into this task:
- The full local CI run (`/ci`: unit, integration, end-to-end, Sonar) and read the real per-stage numbers, not only the tick marks: the earlier runs on phases 1-4 were seen only as green marks, so the end-to-end result, the test counts and the quality gate were never read.
- Playwright specs that touch the changed screens, with `--ux`: `04-provider-profile-flow` and `05-marketplace-advertisement-flow` (the contact-reveal panel now goes through `ContactRevealService`), plus the whole suite as part of the CI run; the earlier `08-accessibility` failure (blank page after login) did not recur in the three CI runs read on 2026-10-20 (66 end-to-end tests passed each time), so it is watched here, not yet explained.
- Rebuild and restart the application, then walk Swagger UI once: authorize, issue a key, list and revoke keys, history and snapshot reads, a contact reveal, and the timeline.
- Open the Viber link on a real device with Viber installed and confirm it opens a chat with the number.

## Related

- [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) — media, PATCH, Basic-auth gap
- [improvement-210](improvement-210-rest-api-feedback-and-comments.md) — feedback and comments
- [improvement-212](improvement-212-api-flow-runner-page.md) — needs administrator and moderator accounts for role-based flows
- [improvement-211](improvement-211-rest-api-testing-ui-and-mcp-readiness-investigation.md) — tooling and MCP readiness

## Operational notes
- token_cost_review: 114715
- token_cost_research: 72142
- token_cost_verification: n/a
- review_signal_ratio: 0 / 0
- context_loading_task_type: Feature, single module
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: implement a scoped task phase by phase
- flows_chosen: /autopilot, /review, /build-and-test, /sync-docs
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 2
- haiku_decomposition_rework_needed: 0 / 2
- adrs_referenced: ADR-087 (marketplace-app), ADR-007 (marketplace-orchestrator), ADR-010 (marketplace-orchestrator)

### Agent calls
- phase 1 controller edits (exact specification) | subagent_type=general-purpose (model haiku) | tokens=77964 | tool_uses=9 | duration_s=38 | mode=background | batch=solo
- code review of uncommitted changes, phase 1 (default effort) | subagent_type=deep-review-orchestrator | tokens=57661 | tool_uses=10 | duration_s=385 | mode=background | batch=solo
- independent web review of the phase 2 authentication design | subagent_type=general-purpose | tokens=72142 | tool_uses=9 | duration_s=56 | mode=background | batch=solo
- phase 2 documentation edits (exact specification) | subagent_type=general-purpose (model haiku) | tokens=70625 | tool_uses=7 | duration_s=35 | mode=background | batch=solo
- code review of uncommitted changes, phase 2 (default effort) | subagent_type=deep-review-orchestrator | tokens=57054 | tool_uses=8 | duration_s=114 | mode=background | batch=solo

### Script/command runs
- bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox | duration_s=430 | mode=background | result=pass (756 tests, 0 failures)
- bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox (phase 2) | duration_s=270 | mode=background | result=pass (774 tests, 0 failures)
- bash docs/architecture/scripts/generate-architecture-model.sh | duration_s=n/a | mode=background | result=pass

### Review angle yield
- dry-kiss-yagni | survived=0 | total_candidates=0 | tokens=42512
- solid | survived=0 | total_candidates=0 | tokens=50256
- precedent | survived=0 | total_candidates=0 | tokens=46175
- phase 2: dry-kiss-yagni | survived=0 | total_candidates=0 | tokens=45808
- phase 2: solid | survived=0 | total_candidates=0 | tokens=45941
- phase 2: precedent | survived=0 | total_candidates=0 | tokens=49562
