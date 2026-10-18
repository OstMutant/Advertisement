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
   - **Phase 2, shared read rules:** move the rules that now live only in the UI into `AuthorizationService` and make the services enforce them (see "Where the read rules live"). This is the groundwork for phases 3 and 4.
   - **Phase 3, audit and history:** global timeline (administrators and moderators), per-entity activity (administrators and moderators see everything, others their own entries), and reading a snapshot so a client can restore with a normal update.
   - **Phase 4, contacts:** reveal endpoint that records the view, and the monthly view counts. Decided: contacts stay open to visitors who are not signed in, as in the UI (they are advertising); the reveal limit stays per IP, and per IP plus user when signed in; the viewer in the recorded view is null for a visitor.
   - **Phase 5, feedback and comments** (the content of [improvement-210](improvement-210-rest-api-feedback-and-comments.md)): list, aggregate, create, comment, delete comment, flag. Moderation (queue, approve, reject, confirm violation) is included now that the scope is everything; it was UI-only in improvement-210, to be confirmed when this phase starts.
   - **Phase 6, media** (the first item of [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md)): upload, list and delete advertisement media.
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
| Audit and history | `AuditQueryService.getEntityActivity`, `getSnapshotContent`, `getTimelinePage`, `countTimeline` | none in the services: the global timeline tab is shown only when `AccessEvaluator.canView()` (= `isPrivileged()`, administrators and moderators); entity history is filtered by the arguments the UI passes (`userId`, `showAll` = privileged), not by a check inside `AuditQueryService` | read-only endpoints; the rules move into `AuthorizationService` and the audit service enforces them (phases 2 and 3) | none |
| Restore | UI loads a snapshot into the form and saves it | the entity's own save rules | none: a client reads the snapshot and sends a normal update | confirm no dedicated endpoint is wanted |
| Contacts | `ContactAccessService.resolveContact`, `recordView`, `countViewsThisMonth` | none in the service: `resolveContact` has no check and `recordView` accepts a null viewer, so the UI reveals contacts to anonymous visitors too | reveal endpoint that records the view (phase 4) | decided: open to visitors who are not signed in, as in the UI; rate limited per IP |

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
orchestrator) is recorded in phase 2.

*Execution and verification.* Unit A goes to one Haiku dispatch with the exact text above, and I
verify its diff myself. Then `/review` of the diff and apply its findings, then
`bash scripts/build-and-test.sh --unit --integration --sandbox` in the background under the activity
monitor, then `/sync-docs`. The full `scripts/ci.sh` run (end-to-end, Sonar) is skipped for this
phase because nothing visible in the UI changes. The task stays open (phases 2 to 6 remain); the run
ends staged and ready for "зроби коміт", with no commit made.

**Decisions recorded for this phase:** `PUT` (full replace of two fields) instead of `PATCH`;
`If-Match` optional as on the other endpoints; delete returns `204` and behaves as the UI;
supported locales hard-coded as `en|uk` in the request validation because the UI's locale list lives
in `marketplace-app` and the REST module cannot import it (same reason as `SORTABLE_FIELDS`).

## Related

- [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) — media, PATCH, Basic-auth gap
- [improvement-210](improvement-210-rest-api-feedback-and-comments.md) — feedback and comments
- [improvement-212](improvement-212-api-flow-runner-page.md) — needs administrator and moderator accounts for role-based flows
- [improvement-211](improvement-211-rest-api-testing-ui-and-mcp-readiness-investigation.md) — tooling and MCP readiness

## Operational notes
- token_cost_review: 57661
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: 0 / 0
- context_loading_task_type: Feature, single module
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: implement a scoped task phase by phase
- flows_chosen: /autopilot, /review, /build-and-test, /sync-docs
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 1
- haiku_decomposition_rework_needed: 0 / 1
- adrs_referenced: none

### Agent calls
- phase 1 controller edits (exact specification) | subagent_type=general-purpose (model haiku) | tokens=77964 | tool_uses=9 | duration_s=38 | mode=background | batch=solo
- code review of uncommitted changes (default effort) | subagent_type=deep-review-orchestrator | tokens=57661 | tool_uses=10 | duration_s=385 | mode=background | batch=solo

### Script/command runs
- bash scripts/activity-monitor.sh -- bash scripts/build-and-test.sh --unit --integration --sandbox | duration_s=430 | mode=background | result=pass (756 tests, 0 failures)
- bash docs/architecture/scripts/generate-architecture-model.sh | duration_s=n/a | mode=background | result=pass

### Review angle yield
- dry-kiss-yagni | survived=0 | total_candidates=0 | tokens=42512
- solid | survived=0 | total_candidates=0 | tokens=50256
- precedent | survived=0 | total_candidates=0 | tokens=46175
