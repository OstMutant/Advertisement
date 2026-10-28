# improvement-186: REST API project-wide PATCH support (media sub-resource done; hypermedia moved to improvement-230)

**Type:** improvement (design-and-implementation bundle, carved out of `improvement-183` item 9 —
"media/photo upload for advertisements via REST" — once exploring that one ask grew into three
related but independently-sizable pieces: media itself, partial-update semantics, and API
discoverability)
**Module:** marketplace-rest-api, marketplace-orchestrator, platform-commons,
attachment-spring-boot-starter, marketplace-app, provider-profile-spring-boot-starter,
user-spring-boot-starter, taxon-spring-boot-starter
**Priority:** high (user-requested top-of-backlog placement, 2026-09-12)
**When:** independent, no blockers. The hypermedia part (former item 3) moved to [improvement-230](improvement-230-rest-hypermedia-decision.md) on 2026-10-28; the media part was done in improvement-213 phase 7; the Basic-auth part in improvement-213 phase 2.

## Current state (verified against real code, 2026-09-12)

- **No multipart/file-upload endpoint exists anywhere in `marketplace-rest-api`.** `grep -rl
  "MultipartFile"` across the whole repo returns zero hits. `AdvertisementApiController`'s own
  Javadoc states "No photo upload via this API." `AdvertisementSaveDto` has no media field;
  `AdvertisementInfoDto` exposes `mediaUrl`/`mediaContentType`/`mediaCount` read-only, populated by
  `AdvertisementDisplayEnrichmentService`.
- **The lower-level machinery already exists and is REST-reachable today**, just unused by
  `marketplace-rest-api`: `AttachmentPort.upload(EntityType, Long entityId, String filename,
  InputStream, long contentLength, String contentType)` persists an `Attachment` row and captures
  an audit snapshot immediately, given an id that already exists — no Vaadin-specific
  temp-session/gallery-commit ceremony required. `AttachmentPort.getByEntityId(EntityType, Long)`
  returns the full `List<AttachmentItemDto>` (each carrying its own `id`). `AttachmentPort.delete(Long
  attachmentId)` removes one. `AttachmentMediaService` (marketplace-orchestrator) already exposes
  all three unchanged (pure delegation) — reachable from a new REST controller with zero starter
  changes.
- **`AdvertisementSaveService.save(dto, actorId, Function<EntityRef, Long> commitGallery)`'s
  `commitGallery` seam is not Vaadin-specific** — `marketplace-app`'s form handler passes
  `this::commitGallery` (real gallery-commit work); `AdvertisementApiController.create()`/`update()`
  today pass a no-op `ref -> null`. The seam itself is generic; REST simply never populates it.
- **`PUT` is a full-replace, and this has a real, currently-silent data-loss consequence.**
  `AdvertisementWriteRequest` requires `title`/`description`/`adKind` but leaves `categoryIds`/
  `cityTaxonId` optional (no `@NotNull`). `AdvertisementSaveService.save()` treats an omitted
  (`null`) `categoryIds`/`cityTaxonId` as "clear it", not "leave unchanged" — a caller who PUTs
  without resending an unrelated field silently wipes it. The only forced pre-read today is the
  `version` (via `If-Match`, sourced from a prior `GET`'s `ETag`) — the rest of the object's current
  state is the caller's own responsibility to carry forward, with no server-side protection against
  forgetting.
- **One PATCH precedent already exists** — `PATCH /api/users/me/settings` — but nothing else in
  the API supports partial updates, and no shared `JsonNullable`-style (present/absent/null
  tri-state) mechanism exists to build more of them; plain `Long`/`Set<Long>` fields cannot
  distinguish "omitted" from "sent as null" in Jackson deserialization.
- **No hypermedia/action-discovery mechanism exists anywhere in the API.** Every response is a flat
  DTO; a caller must already know (from Swagger, docs, or hardcoded URL templates) what it's allowed
  to do next from a given resource. `marketplace-app/DECISIONS.md` ADR-080 already compared and
  explicitly **rejected** Spring HATEOAS and an envelope response body for **list-endpoint
  pagination specifically** ("more framework machinery than this project's own explicit-over-implicit
  principle favors"), in favor of RFC 8288 `Link` header + `X-Total-Count` (already shipped, used by
  all 3 list endpoints). ADR-080 never addressed single-resource action-discovery (create/edit/
  delete/media affordances) — a different question it didn't rule on either way.
- **Vaadin's own upload caps (`AttachmentUploadButton.MAX_FILES = 10`, `MAX_FILE_SIZE = 50MB`) live
  as package-private constants inside a `marketplace-app` Vaadin class** — unreachable from
  `marketplace-rest-api` (no Vaadin dependency, wrong module) even if REST wanted to enforce the
  same caps.
- **Real-world research (2026-09-12, web search):** combining a JSON metadata part with file parts
  in one `multipart/form-data` request is an accepted, common REST pattern (not itself
  nonstandard). However, industry sources are consistent that **append-shaped operations (adding an
  attachment to an existing resource) belong on `POST`, not `PUT`** — `PUT`'s idempotency contract
  (repeating it must not change the outcome) is violated by naively re-running an "upload and
  append" step, since re-uploading appends a duplicate rather than reproducing the same end state.
  Sources: [Tyk — REST API file upload guidance](https://tyk.io/blog/api-design-guidance-file-upload/),
  [REST API Design: What is Idempotency? (Medium)](https://medium.com/@reetesh043/rest-api-design-what-is-idempotency-18218e1ff73c),
  [REST API Multipart File Upload Guide (ASOasis)](https://asoasis.tech/articles/2026-04-01-0253-rest-api-file-upload-multipart-guide/).
  Also confirmed: Swagger UI does **not** natively support clicking a runtime response's hypermedia
  link to open the next operation pre-filled — OpenAPI's own `Links` object is a static, design-time
  description, not wired to real response data, and Swagger UI's own support for even that static
  feature is reportedly broken ([swagger-ui#7533](https://github.com/swagger-api/swagger-ui/tasks/7533)).
  A dedicated tool exists that adds exactly this on top of an existing Swagger UI + OpenAPI setup,
  for HAL-shaped (`_links.rel.href`) responses specifically:
  [swagger-hal-navigator](https://github.com/mpietrewicz/swagger-hal-navigator) — "renders links
  from the HAL response body's `_links`... clicking a link opens the corresponding documented API
  operation and pre-fills its parameters." Requires the app to already expose a working Swagger UI
  + OpenAPI document (already true here via springdoc) and requires the response shape to follow
  the HAL `_links` convention specifically, not an arbitrary custom shape.

## Why change

Three related gaps surfaced while designing improvement-183 item 9's media-upload ask, each with
its own real cost today:
1. There is genuinely no way to attach a photo to an advertisement via the REST API at all, despite
   the underlying `AttachmentPort` already supporting it generically.
2. `PUT`'s current full-replace semantics silently destroy data a caller didn't intend to touch —
   a real footgun for any REST client that doesn't already know to always resend every field.
3. A REST client (or a developer exploring the API) has no way to discover "what can I do from
   here" other than already knowing the full URL space in advance — Swagger documents the whole
   surface (a static, "vertical" catalog) but nothing tells a caller, from an actual response, which
   of those documented operations apply *to this specific resource, for this actor, right now*.

## Expected benefit

- Advertisements (and by the same mechanism, any future entity) can have photos attached, listed,
  and removed via the REST API, matching what the Vaadin UI can already do.
- A REST client can safely change one field without needing to first fetch and resend every other
  field — removes a real, currently-silent data-loss class of bug.
- Single-resource responses become self-describing: a client (script, SDK, or `swagger-hal-navigator`-
  augmented Swagger UI) can see directly in the response which actions (edit/delete/add media) are
  actually available to the current caller for that specific resource, instead of guessing or
  hardcoding authorization rules client-side.
- An explicit login/root entry point (`GET /api/me`) gives a caller its own available top-level
  actions by role, without needing prior knowledge of the whole API surface.

## Approach

### 1. Advertisement media as its own REST sub-resource (absorbs improvement-183 item 9)

**Done 2026-10-20 (phase 7 of [improvement-213](../completed/tasks/improvement-213-rest-api-revision-parity-with-ui.md)):** `GET`, `POST` (a batch of files in one `multipart/form-data` request, one history entry per request), `POST …/videos` and the single-id `DELETE` are built with the edit form's rules (10 items, 50 MB, the same types, an attachment must belong to the advertisement), and restoring an advertisement to a snapshot restores its gallery. Not built, because the UI has neither: creating an advertisement together with its files in one call, and bulk delete. The rest of this task (PATCH, hypermedia) is unchanged.

- `POST /api/advertisements/{id}/media` — append one or more files, `multipart/form-data`. Delegates
  to `AttachmentPort.upload()` (via a new, narrow `marketplace-orchestrator` service — existence
  check + `AuthorizationService.requireCanOperate` + delegate — mirroring how every other
  controller method is a thin adapter over exactly one orchestrator call). Deliberately `POST`, not
  folded into `PUT`, since appending a file is not idempotent (see "Current state" research above).
  Optionally supports an `Idempotency-Key` header later (client-supplied key, server dedupes) if
  retry-safety is needed without changing the HTTP semantics.
- `GET /api/advertisements/{id}/media` — list current media (`AttachmentPort.getByEntityId`), each
  item carrying its own `id` for targeting delete.
- `DELETE /api/advertisements/{id}/media/{attachmentId}` — remove one (`AttachmentPort.delete`),
  naturally idempotent. **Must verify `attachmentId` actually belongs to entity `{id}`** before
  deleting — checking only "does the actor own advertisement `{id}`" without also checking
  attachment-to-entity ownership is an IDOR gap (actor could delete another entity's attachment by
  guessing/reusing an id).
  **Open sub-question:** single-id delete only (client loops for a batch, mirrors how even the
  Vaadin gallery itself has no batch-delete primitive today — `AttachmentPort.delete()` is
  singular) vs. a bulk `DELETE .../media?ids=1,2,3` (one round trip, needs either a new
  `AttachmentPort.deleteAll(Set<Long>)` or an orchestrator-level loop wrapped in one transaction).
  Not decided — pick when implementing based on real client need.
- `POST /api/advertisements` (create) additionally accepts `multipart/form-data` (a `data` JSON
  part + optional `media` file parts) so an advertisement can be created with its initial gallery in
  one call, matching the Vaadin form's own create-with-gallery flow. `POST` is not required to be
  idempotent, so combining create + initial upload here doesn't hit the same problem `PUT` would.
  `PUT /api/advertisements/{id}` (update) stays pure JSON, no files — idempotency preserved; adding
  photos during an edit goes through the same `POST /media` endpoint above.
- `AttachmentUploadButton.MAX_FILES`/`MAX_FILE_SIZE` need to move out of their current
  `marketplace-app`-only, package-private home into a shared location (`platform-commons`, next to
  `AdvertisementSaveDto.CATEGORY_MAX_COUNT`'s own precedent) so both the Vaadin form and the new
  REST endpoint enforce the identical cap from one source, not two copies that can drift.

### 2. Partial-update (PATCH) support, audited project-wide — not just Advertisement

- **Superseded by the approved Plan below (JSON Merge Patch, no `JsonNullable`).** Add `PATCH /api/advertisements/{id}` for the fields where "send it, or leave it alone" is the
  real need (`categoryIds`, `cityTaxonId`, and any other genuinely-optional field) — using a
  tri-state wrapper (Jackson `JsonNullable<T>` or equivalent: absent / explicit null / value) so
  the server can actually distinguish "not sent" from "sent as empty", unlike today's plain
  `Long`/`Set<Long>` fields.
- Audit every other REST-exposed domain for the identical symptom (an optional field on a `PUT`
  whose omission silently wipes rather than preserves) — `ProviderProfileApiController`,
  `TaxonApiController` — and add the equivalent `PATCH` wherever the same gap exists, mirroring the
  already-shipped `PATCH /api/users/me/settings` precedent.
- `PUT` itself is not changed by this — stays full-replace by design (REST convention, and matches
  what the existing `ETag`/`If-Match` optimistic-locking flow already assumes: full state resent
  each time).

### 3. Hypermedia — moved to improvement-230

Moved on 2026-10-28: whether HAL `_links` and a `GET /api/me` entry point are needed is decided from the evidence of the MCP trial, see [improvement-230](improvement-230-rest-hypermedia-decision.md). The design drafted here (per-actor `_links`, `GET /api/me`, `swagger-hal-navigator`, pagination links in list bodies left out because they would reverse the pagination decision) is carried over there.

### 4. and 5. HTTP Basic hardening — moved to improvement-213 phase 2

The Basic-auth brute-force gap on `POST /api/api-keys` and the 500 returned when Basic credentials reach a bearer-only endpoint are implemented in [improvement-213](../completed/tasks/improvement-213-rest-api-revision-parity-with-ui.md), phase 2: a separate security chain for API-key issuance, a failed-login limit, and a Bearer challenge on every other `/api/**` endpoint.

## Related

- [improvement-183](../completed/tasks/improvement-183-rest-api-and-taxon-ui-follow-ups.md) item 9 — the original
  "media/photo upload for advertisements via REST" ask this issue absorbs and supersedes; item 9's
  own text should be updated to point here rather than duplicate this plan.
- `marketplace-app/DECISIONS.md` ADR-080 — the existing pagination/HATEOAS-rejection decision this
  issue's item 3 explicitly revisits (list-body-envelope portion only; the filter/sort-on-existing-DTOs
  portion is untouched).
- `PATCH /api/users/me/settings` (`marketplace-app/DECISIONS.md`, see improvement-183 item 16) — the
  existing partial-update precedent item 2 above mirrors.
- `attachment-spring-boot-starter/README.md`/`DECISIONS.md` — `AttachmentPort`'s existing
  generic-by-`EntityType` design (ADR-003 there) is exactly what makes item 1 above possible with no
  starter changes.
- [improvement-230](improvement-230-rest-hypermedia-decision.md) — the hypermedia decision carved out of this task

## Plan (approved 2026-10-28)

Researched with the `deep-researcher` agent (topic `rest-patch-support`). **Approved with all eight recommended answers:** merge patch with an own helper; 400 for an invalid result; accept `application/merge-patch+json` and `application/json`; reject an unknown field with 400; convert the existing settings `PATCH` in this task and require `If-Match`; the Swagger body points at the existing write request; the ETag header mirrors each resource's `PUT`; an empty patch `{}` is saved like a `PUT` with unchanged values. This plan replaces the `JsonNullable` design in Approach section 2 above.

### Goal

Let a REST API caller change one or two fields of a resource without resending the rest, so that a
field the caller did not mention is never wiped by accident. `PUT` stays as it is: a full replace.

### The problem this solves

Today two resources lose data silently when a caller leaves out an optional field on `PUT`:

| Resource | Fields that are cleared when left out | Evidence |
|---|---|---|
| Advertisement (`PUT /api/advertisements/{id}`) | `categoryIds`, `cityTaxonId` | `AdvertisementWriteRequest` leaves them optional; `AdvertisementSaveService.save()` turns a missing list into "no categories" and a missing city into "no city" |
| Provider profile (`PUT /api/provider-profiles/{id}`) | `about`, `categoryIds`, `cityTaxonId`, `phone`, `telegram`, `viber` | `ProviderProfileWriteRequest` leaves them optional; `ProviderProfileSaveService.save()` replaces categories/city and overwrites the contact row with what was sent |

The other `PUT`s need no `PATCH`: taxon (full translation list, every language required), user (name
and role, both required), feedback and reply (rating/text required), and `/me/locale` (one required
field). Leaving out a field there is a 400, never a silent wipe.

**Found on the way, worth knowing first:** the one `PATCH` that exists today,
`PATCH /api/users/me/settings`, is not a partial update and has its own silent wipe. It takes four
page sizes (all effectively required: a missing one becomes 0 and fails the minimum of 5), but the
settings hold six. Every call resets the other two (`feedbackPageSize`, `moderationPageSize`) to the
default. A call without `If-Match` most likely ends in a 500 instead of a 400 (read from the code, not
run).

### The means needed

Recommended: **JSON Merge Patch** (the internet standard RFC 7396), applied by a small piece of our
own code, with no new library.

How it works for a caller: send only the fields to change. A field that is absent keeps its value; a
field sent as `null` is cleared (for a required field, such as a title, that is a 400); a list is
replaced as a whole. `If-Match` with the version from the last `GET` is required, exactly as for `PUT`.

How it works inside: the server reads the resource's current state (the same shape the history
records), lays the caller's fields over it, checks the result with the same validation rules `PUT`
uses, and saves it through the same save path. So permissions, validation, the history entry and the
version check all stay in one place.

The four mechanisms compared:

| | JSON Merge Patch (recommended) | `JsonNullable` fields (the task's earlier idea) | JSON Patch (RFC 6902) | Only document "GET, then PUT" |
|---|---|---|---|---|
| Tells "not sent" from "sent as null" | yes | yes | yes | no |
| New dependency | none | `jackson-databind-nullable` 0.2.13 (a 0.x library; Jackson 3 support since mid-2026) | a JSON-P implementation or own code (the usual Java library is Jackson 2 only, last release 2020) | none |
| Validation after merging | the existing rules on the existing request record | extra checks per field | own checks | nothing new |
| New code per resource | about 15 lines | a second request record plus field-by-field merge | an operation interpreter | none |
| Swagger UI | a normal JSON body, only the changed fields | wrapper types need documentation support | a list of operations, no field hints | unchanged |
| Agents and other callers | easy: send the fields you want | easy | harder: paths and operation names | the trap stays |
| Recommended by | the RFC, Zalando and Microsoft Azure API guidelines | (no guideline) | Zalando as second choice | (none) |

### What the result will look like

- New `PATCH /api/advertisements/{id}` and `PATCH /api/provider-profiles/{id}`, both taking
  `application/merge-patch+json` (and plain `application/json`, see decision 3), with `If-Match`.
  Example: `{"cityTaxonId": 7}` changes the city and leaves everything else untouched.
- Responses, errors and the history entry are the same as for that resource's `PUT`: 200 with the
  resource, 400 for invalid fields, 403 for someone who may not edit it, 404 for an unknown id, 412
  for a stale version.
- If you approve decision 5: `PATCH /api/users/me/settings` becomes a real merge patch over all six
  page sizes and stops resetting two of them; a missing `If-Match` becomes a 400.
- `PUT` everywhere is unchanged.
- Swagger UI shows the two new operations with an example body; the Postman collection, the Level 3
  integration tests and the REST Playwright flow cover them.

### Cost

Estimates, not measurements.

| Executor | Steps | Tokens (estimate) |
|---|---|---|
| sonnet | 1, 3, 4, 5, 6, 7, 9 | about 800k-900k in total |
| haiku | 2, 8 | about 50k-60k in total |
| main thread | 10 (running the test chain) | not measurable |

Work in time: roughly one working day of agent time including the test runs (the integration tests
need Docker). If every sonnet step ran on opus instead, the token count would be similar and the price
higher; no step needs opus.

### Risks

- **Accepting `null` as "clear it".** A caller who sends `"phone": null` meaning "I don't know" will
  clear the phone. This is how the standard works; the Swagger description must say so.
- **Swagger UI with `application/merge-patch+json`.** Not verified that Swagger UI's "Try it out"
  sends this media type well; accepting plain `application/json` too (decision 3) removes the risk.
- **Generated MCP tools.** If the planned MCP server is generated from the OpenAPI document, the
  generator may handle only `application/json`; again covered by decision 3 (not verified).
- **An empty patch `{}`** still saves and writes a history entry, like a `PUT` with unchanged values
  (the audit side does not compare snapshots).
- **Reading contacts for the merge.** The provider-profile patch reads the current phone/Telegram/Viber
  on the server to keep them. They are never returned unless the save succeeds for an allowed caller,
  and the response does not contain contacts at all, so nothing leaks; a test checks the 403 case.
- **Which Jackson mapper converts the merged result.** The plan uses a plain mapper owned by the helper
  rather than Spring's shared one, so it does not depend on the shared mapper's settings; field types
  here are simple (text, numbers, id lists, enums), so the two cannot disagree in practice.

### Where the project's practice differs from best practice

1. **400 vs 422 for an invalid result.** RFC 5789 suggests 422 when a well-formed patch would make
   the resource invalid. The project answers every validation failure with 400 and the same
   `fieldErrors` body. The plan keeps 400 (the project's way). Switching to 422 would make `PATCH`
   differ from `PUT` for the same mistake.
2. **PATCH vs PUT for updates.** Zalando and the Azure guidelines prefer `PATCH` over a full `PUT`.
   The brief keeps `PUT` as a full replace, so `PATCH` is added beside it. Not reopened.
3. **ETag on write responses.** The Azure guidelines say every response that returns a resource should
   carry an `ETag`. The advertisement and provider-profile `PUT` return the version only in the body;
   the user `PUT` sets the header. The plan makes each `PATCH` do what that resource's `PUT` does.
   Adding the header to both would cost a few lines per endpoint.
4. **The earlier design in the task file** names `JsonNullable`. This plan recommends merge patch
   instead, for the reasons in the table. Your call.

### Decisions waiting for you

1. **Mechanism:** JSON Merge Patch with our own small helper (recommended), or `JsonNullable`, or
   JSON Patch, or documentation only.
2. **Status code for an invalid result:** 400 like `PUT` (recommended, project practice) or 422 (RFC
   5789's suggestion).
3. **Media types:** accept both `application/merge-patch+json` and `application/json` as merge patch
   (recommended: keeps the existing settings callers and simple tools working) or only the merge-patch
   type (strict; plain JSON gets 415).
4. **Unknown field in a patch** (for example a typo `cityTaxonID`): reject with 400 (recommended,
   because otherwise the caller thinks the change was made) or ignore, like `PUT` probably does today
   (not verified).
5. **The existing settings `PATCH`:** convert it to a real merge patch over all six page sizes and
   require `If-Match` (recommended, fixes a live bug), or leave it out of this task and file it
   separately.
6. **Swagger schema of the patch body:** point it at the existing write request (recommended, no extra
   code; its "required" markers are then slightly misleading, the description explains) or add a
   documentation-only record per resource with nothing marked required (more code, a second field list
   to keep in step).
7. **ETag header on the PATCH response:** mirror each resource's `PUT` (recommended for symmetry) or
   add the header to advertisement and provider-profile `PUT` and `PATCH` alike.
8. **Empty patch `{}`:** save it like a `PUT` with unchanged values (recommended, least code) or answer
   400.

## Execution plan

(exact content for a weaker model, exact files and behavior for a stronger one).

### Preconditions

- The person has answered the decisions in `plan-human.md`. This plan is written for the
  recommended answers: (1) JSON Merge Patch with an own helper, (2) 400, (3) accept
  `application/merge-patch+json` and `application/json`, (4) reject unknown members, (5) convert the
  settings PATCH, (6) schema points at the existing write record, (7) mirror each resource's PUT,
  (8) an empty patch saves like PUT. Where an answer differs, the step that it touches says what
  changes. If decision 1 is not merge patch, stop: this plan does not apply.
- Every step follows the project's Approval Rule (plan, ask, then act), `CLAUDE.md`, `.claude/rules.md`
  and `.claude/rules/marketplace-rest-api.md` (read a file in `marketplace-rest-api/` first so it
  loads). Javadoc/comments: consult `.claude/skills/module-doc-standards/SKILL.md`; one-line comments,
  no task numbers, no ADR numbers.
- `PUT` endpoints are not changed in behavior. Only a private-method extraction inside the two
  controllers is allowed (step 3, 4).

### Facts the steps rely on (verified 2026-10-28)

- `AdvertisementApiController.update()` builds `AdvertisementSaveDto(id, title, description, adKind, categoryIds, cityTaxonId, ETagUtil.requireIfMatch(ifMatch))`, calls `saveService.save(dto, actorId, ref -> null)`, returns `enrichmentService.enrichSingle(readService.findById(savedId).orElseThrow(), SupportedLocales.DEFAULT)`. No ETag header on the response.
- `ProviderProfileApiController.update()` builds `ProviderProfileSaveDto(id, kind, about, categoryIds, cityTaxonId, phone, telegram, viber, version)`, calls `saveService.save(dto, actorId, actorId)`, returns `enrich(readService.findById(savedId).orElseThrow(), SupportedLocales.DEFAULT)`. No ETag header.
- `AdvertisementSaveService` has private `buildCurrentSnapshot(AdvertisementInfoDto)`; `ProviderProfileSaveService` has private `toSnapshot(ProviderProfileDto)` (reads contacts through `ContactAccessService`) and public `findById(Long)`.
- `AdvertisementSnapshotDto(title, description, adKind, List<Long> categoryIds, cityTaxonId, attachmentSnapshotId, ...)`; `ProviderProfileSnapshotDto(kind, about, List<Long> categoryIds, cityTaxonId, phone, telegram, viber, ...)`. `categoryIds` is never null in a built snapshot.
- `UserApiController.updateSettings()` (`PATCH /me/settings`, operationId `updateMySettings`) builds a fresh `UserSettingsDto` with 4 of its 6 page sizes and `ETagUtil.parseIfMatch` (nullable) passed to a `long` builder field.
- Spring 7's `JacksonJsonHttpMessageConverter` reads `application/json` and `application/*+json` by default.
- Jackson 3.1.5 `tools.jackson.databind.node.ObjectNode` has `properties()`, `set(String, JsonNode)`, `remove(String)`, `deepCopy()`.
- `ApiExceptionHandler` maps `MethodArgumentNotValidException` to 400 `ValidationErrorResponse(Map<String,String> fieldErrors)`, `IllegalArgumentException` to 400 `ErrorResponse`, `NoSuchElementException` to 404, `StaleWriteException` to 412, `AccessDeniedException` (orchestrator) to 403. It has no `ConstraintViolationException` handler; do not add a global one (deep starter validation would change status).
- Controller unit tests build controllers with `new XController(...)` and `RestApiMockMvcTestSupport.mockMvc(...)` (plain `JsonMapper.builder().build()`, `ApiExceptionHandler` as advice).
- New operations must pass three gates: `ArchitectureRulesTest` (explicit unique `operationId`), `integration-tests` `OperationCoverageTest` (Level 3 scenario or allow-list), and `docs/api-collections/scripts/check-collection.mjs` (Postman request or allow-list).

### Steps

### Step 1 -- merge-patch helper and its error mapping

- **Depends on:** none
- **Files:** new `marketplace-rest-api/src/main/java/org/ost/restapi/api/patch/MergePatchSupport.java`; new `marketplace-rest-api/src/main/java/org/ost/restapi/api/patch/PatchValidationException.java`; `marketplace-rest-api/src/main/java/org/ost/restapi/api/error/ApiExceptionHandler.java`; new `marketplace-rest-api/src/test/java/org/ost/restapi/api/patch/MergePatchSupportTest.java`; `marketplace-rest-api/src/test/java/org/ost/restapi/api/error/ApiExceptionHandlerTest.java`
- **Executor:** sonnet
- **Input:** RFC 7396 section 2 pseudo-code; `ActivityHistorySupport` (a Spring `@Component` "support" class in `api.audit`, the naming and shape to mirror); `ApiExceptionHandler`.
- **Change (behavior and constraints, no ready code):**
  - `MergePatchSupport`: `@Component`, `@RequiredArgsConstructor`, one injected dependency `jakarta.validation.Validator`. It owns `private static final JsonMapper` built with `JsonMapper.builder().build()` (do not inject a mapper bean: the context has several `ObjectMapper` beans and the project forbids relying on `@Primary`).
  - One public generic method taking `(@NonNull T current, @NonNull JsonNode patch, @NonNull Class<T> type)` where `T extends Record`, returning `T`:
    1. `patch` not an object node -> `IllegalArgumentException` ("A merge patch must be a JSON object") -> 400.
    2. Any member name of `patch` that is not a record component name of `type` -> `IllegalArgumentException` naming the field -> 400. (Decision 4 = ignore: drop this check.)
    3. Convert `current` to an `ObjectNode`, apply RFC 7396 exactly (recursive: object patch over object target; `null` member removes it; any non-object value replaces; a non-object patch replaces the target), on a copy.
    4. Convert the merged tree to `type`; a Jackson exception (type mismatch, unreadable enum, null for a primitive) -> `IllegalArgumentException` with a short message without echoing the whole body -> 400.
    5. Validate the result with the `Validator`; if any violation -> throw `PatchValidationException` carrying `Map<String,String>` of property path -> message (first message per path wins, same as the existing `MethodArgumentNotValidException` handler).
  - `PatchValidationException`: unchecked, holds the field-error map (Lombok `@Getter`).
  - `ApiExceptionHandler`: add a handler for `PatchValidationException` -> 400 `ValidationErrorResponse(fieldErrors)`; same body shape as a failed `@Valid` on PUT. (Decision 2 = 422: use 422 here only.)
  - Class Javadoc on `MergePatchSupport`: one sentence on what it does (applies an RFC 7396 merge patch to a write record and validates the result).
- **Verification:** `MergePatchSupportTest` (plain JUnit, real `Validation.buildDefaultValidatorFactory().getValidator()`, a small test record with one `@NotBlank String`, one nullable `Long`, one `@Size(max=2) Set<Long>`): absent field keeps value; `null` on nullable clears; `null` on `@NotBlank` -> `PatchValidationException` with that key; value replaces; array replaced whole; `{}` returns an equal record; non-object patch -> `IllegalArgumentException`; unknown member -> `IllegalArgumentException`; wrong type (`"x"` for the `Long`) -> `IllegalArgumentException`; `@Size` exceeded -> `PatchValidationException`. `ApiExceptionHandlerTest`: the new handler returns 400 and the map. Run `bash scripts/build-and-test.sh --unit --sandbox` scoped as the script allows (re-read `scripts/CLAUDE.md` first).
- **Sources:** https://www.rfc-editor.org/rfc/rfc7396, https://www.rfc-editor.org/rfc/rfc5789, https://cassiomolin.com/2019/06/10/using-http-patch-in-spring/

### Step 2 -- current-state readers in the orchestrator

- **Depends on:** none (parallel with step 1)
- **Files:** `marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/AdvertisementSaveService.java`, `marketplace-orchestrator/src/main/java/org/ost/orchestrator/services/ProviderProfileSaveService.java`
- **Executor:** haiku
- **Input:** the two files as they are.
- **Change:**
  - `AdvertisementSaveService.java`: add the import line `import java.util.Optional;` directly after `import java.util.NoSuchElementException;`. Then replace
    ```java
        public boolean isAvailable() {
            return advertisementPortFactory.findIfAvailable().isPresent();
        }
    ```
    with
    ```java
        public boolean isAvailable() {
            return advertisementPortFactory.findIfAvailable().isPresent();
        }

        /** Returns the advertisement's current editable state in its history-snapshot shape, empty when the id is unknown. */
        public Optional<AdvertisementSnapshotDto> findCurrentSnapshot(@NonNull Long id) {
            return advertisementPortFactory.get().findById(id).map(this::buildCurrentSnapshot);
        }
    ```
  - `ProviderProfileSaveService.java` (already imports `java.util.Optional`): replace
    ```java
        public boolean isAvailable() {
            return providerProfilePortFactory.findIfAvailable().isPresent();
        }
    ```
    with
    ```java
        public boolean isAvailable() {
            return providerProfilePortFactory.findIfAvailable().isPresent();
        }

        /** Returns the profile's current editable state, contact channels included, in its history-snapshot shape; empty when the id is unknown. */
        public Optional<ProviderProfileSnapshotDto> findCurrentSnapshot(@NonNull Long id) {
            return findById(id).map(this::toSnapshot);
        }
    ```
- **Verification:** the reactor compiles (`bash scripts/build-and-test.sh` without test flags, re-read `scripts/CLAUDE.md` first); `git diff` shows only these additions.
- **Sources:** none (project code).

### Step 3 -- `PATCH /api/advertisements/{id}`

- **Depends on:** 1, 2
- **Files:** `marketplace-rest-api/src/main/java/org/ost/restapi/api/AdvertisementApiController.java`, `marketplace-rest-api/src/test/java/org/ost/restapi/api/AdvertisementApiControllerTest.java`
- **Executor:** sonnet
- **Input:** the controller, its test, step 1 and 2 results.
- **Change:**
  - Add `MergePatchSupport` as a constructor dependency (Lombok `@RequiredArgsConstructor` field).
  - Add to `AdvertisementWriteRequest` a static factory `from(AdvertisementSnapshotDto)` mapping title, description, adKind, `Set.copyOf(categoryIds)`, cityTaxonId.
  - Extract the body of `update()` after the request is known into one private method (request, id, version, actorId -> enriched `AdvertisementInfoDto`); `update()` calls it unchanged in behavior.
  - New method `patch(...)`: `@PatchMapping(value = "/{id}", consumes = {"application/merge-patch+json", MediaType.APPLICATION_JSON_VALUE})` (decision 3 strict: only the first), `@SecurityRequirement(name = "bearerKey")`, `@Operation(operationId = "patchAdvertisement", summary = "Partially update an advertisement", description = ...)`. Parameters: `@AuthenticationPrincipal Long actorId`, `@PathVariable Long id`, `@RequestBody tools.jackson.databind.JsonNode patch`, `If-Match` header as in `update()`. Order: `ETagUtil.requireIfMatch` (400 when missing) -> `saveService.findCurrentSnapshot(id)` or `NoSuchElementException` (404) -> `mergePatchSupport.apply(AdvertisementWriteRequest.from(current), patch, AdvertisementWriteRequest.class)` -> the extracted private save method with the If-Match version (stale -> 412 from the save path; not owner -> 403 from the save path).
  - OpenAPI: description says JSON Merge Patch (RFC 7396); send only the fields to change; absent keeps, `null` clears `categoryIds`/`cityTaxonId`, `null` on title/description/adKind is 400; `categoryIds` is replaced as a whole; unknown field is 400; If-Match from the last GET's ETag; caller must own the advertisement; 412 on a stale version. `@io.swagger.v3.oas.annotations.parameters.RequestBody` with two `@Content` entries (merge-patch first, then application/json), each `schema = @Schema(implementation = AdvertisementWriteRequest.class)` and the example `{"cityTaxonId": 5}` (decision 6 = doc record: add a nested `AdvertisementPatchDocument` record with the same five fields and no constraints and point the schema at it).
  - Response: same as `update()` (no ETag header; decision 7 alternative: add `ETagUtil.withVersion` to both).
- **Verification:** new tests in `AdvertisementApiControllerTest` (construct `MergePatchSupport` with a real validator): per field absent / `null` / value for `categoryIds` and `cityTaxonId` (capture the `AdvertisementSaveDto` passed to `saveService.save` and assert the kept/cleared/changed value plus the If-Match version); `title: null` -> 400 with `fieldErrors.title`; unknown member -> 400; missing If-Match -> 400; unknown id (`findCurrentSnapshot` empty) -> 404; `StaleWriteException` from save -> 412; orchestrator `AccessDeniedException` from save -> 403; `Content-Type: text/plain` -> 415; `application/json` accepted (decision 3). Existing `update()` tests still pass unchanged.
- **Sources:** https://www.rfc-editor.org/rfc/rfc7396, https://www.rfc-editor.org/rfc/rfc5789, https://docs.spring.io/spring-framework/docs/7.0.3/javadoc-api/org/springframework/http/converter/json/JacksonJsonHttpMessageConverter.html, https://opensource.zalando.com/restful-api-guidelines/#148

### Step 4 -- `PATCH /api/provider-profiles/{id}`

- **Depends on:** 1, 2 (same pattern as 3; can run after 3 to copy its shape)
- **Files:** `marketplace-rest-api/src/main/java/org/ost/restapi/api/ProviderProfileApiController.java`, `marketplace-rest-api/src/test/java/org/ost/restapi/api/ProviderProfileApiControllerTest.java`
- **Executor:** sonnet
- **Input:** step 3's finished controller as the model.
- **Change:** identical structure to step 3: `MergePatchSupport` dependency; `ProviderProfileWriteRequest.from(ProviderProfileSnapshotDto)` mapping kind, about, `Set.copyOf(categoryIds)`, cityTaxonId, phone, telegram, viber; extract the save part of `update()` into a private method (keeps `saveService.save(dto, actorId, actorId)`); new `patch` with operationId `patchProviderProfile`, summary "Partially update a provider profile", the same consumes, If-Match, order of checks and response as `update()`. Description: merge-patch rules as in step 3, `null` clears about/categoryIds/cityTaxonId/phone/telegram/viber, `null` on kind is 400, `kind=SUPPORT` still requires a privileged caller, contacts are never returned by this call. Example body `{"telegram": "plumber_master"}`.
- **Verification:** tests in `ProviderProfileApiControllerTest`: absent / `null` / value for each of about, categoryIds, cityTaxonId, phone, telegram, viber (captured `ProviderProfileSaveDto`); `kind: null` -> 400; invalid phone pattern -> 400 `fieldErrors.phone`; missing If-Match -> 400; unknown id -> 404; stale -> 412; 403 from save -> 403 and the response body contains no phone/telegram/viber value.
- **Sources:** as step 3.

### Step 5 -- settings PATCH becomes a real merge patch (only if decision 5 = convert)

- **Depends on:** 1
- **Files:** `marketplace-rest-api/src/main/java/org/ost/restapi/api/UserApiController.java`, `marketplace-rest-api/src/test/java/org/ost/restapi/api/UserApiControllerTest.java`
- **Executor:** sonnet
- **Input:** `UserApiController.updateSettings`, `UserSettingsWriteRequest`, `UserSettingsDto` (6 page sizes, `version`, `schemaVersion`).
- **Change:** `UserSettingsWriteRequest` gets `feedbackPageSize` and `moderationPageSize` with the same `@Min/@Max`, plus a static `from(UserSettingsDto)`. `updateSettings` keeps its path, operationId `updateMySettings` and response type; it takes `@RequestBody JsonNode patch`, consumes both media types, uses `ETagUtil.requireIfMatch` (missing -> 400, not 500), loads `userProfileService.loadSettings(actorId)`, merges with `MergePatchSupport`, then builds `UserSettingsDto` from all six merged fields and the If-Match version (`toBuilder()` on the loaded settings keeps `schemaVersion`). `null` on a page size -> 400. Update the description and example (`{"adsPageSize": 30}`), and the snapshot/restore descriptions that mention "PATCH" stay valid.
- **Verification:** existing `updateSettings_*` tests still pass (a full 4-field body is a valid merge patch); new: `{"adsPageSize":30}` keeps the other five (assert the `UserSettingsDto` passed to `saveSettings`, especially `feedbackPageSize`/`moderationPageSize` unchanged); missing If-Match -> 400; `adsPageSize: null` -> 400; unknown member -> 400.
- **Sources:** https://www.rfc-editor.org/rfc/rfc7396

### Step 6 -- Level 3 integration scenarios

- **Depends on:** 3, 4, (5)
- **Files:** `integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/AdvertisementMatrixScenarioTest.java`, `ProviderProfileMatrixScenarioTest.java`, `MyAccountMatrixScenarioTest.java` (same folder); read `AdvertisementAuthorizationScenarioTest`/`ProviderProfileAuthorizationScenarioTest` and `AuditHistoryScenarioTest` for helpers first; read `.claude/rules/integration-tests.md`.
- **Executor:** sonnet
- **Input:** the existing matrix tests' helpers (`registerUserAndIssueApiKey`, `etagOf`, role users).
- **Change:** add PATCH cases with real persistence:
  - Roles: anonymous -> 401; owner -> 200; stranger -> 403; moderator and administrator -> the same outcome their `PUT` gets in the existing matrix (check those tests, do not assume).
  - Fields: create with categories and city (and contacts for the profile), PATCH only another field, `GET` shows categories/city/contacts kept; PATCH `{"cityTaxonId": null}` clears the city only; PATCH `{"categoryIds": [x]}` replaces the set.
  - Version: stale If-Match -> 412; missing -> 400; after a successful PATCH the old ETag is stale.
  - Validation: `title: null` / `kind: null` -> 400; unknown member -> 400; `text/plain` -> 415 (assert `Accept-Patch` header if present; record in the task file whether Spring set it).
  - History: one PATCH adds exactly one entry to `/history` and its snapshot shows the merged state.
  - Settings (if step 5 ran): set feedback/moderation sizes through the full body, PATCH `{"adsPageSize": 30}`, `GET` shows the other five unchanged; missing If-Match -> 400.
- **Verification:** `bash scripts/build-and-test.sh --unit --integration --sandbox` (via the activity-monitor wrapper as `.claude/rules.md` says), `OperationCoverageTest` green without allow-list entries for `patchAdvertisement`/`patchProviderProfile`.
- **Sources:** https://www.rfc-editor.org/rfc/rfc5789

### Step 7 -- Postman collection and the REST Playwright flow

- **Depends on:** 3, 4, (5)
- **Files:** `docs/api-collections/postman/marketplace-rest-api.postman_collection.json`; `playwright/e2e/10-rest-api-flow.spec.js`; read `docs/api-collections/postman/README.md`, `docs/api-collections/scripts/README.md`, `playwright/CLAUDE.md` / `.claude/rules/playwright.md` first.
- **Executor:** sonnet
- **Input:** the existing `updateAdvertisement` / `updateProviderProfile` / `updateMySettings` requests in the collection (their step metadata, variables and test scripts).
- **Change:** add a `patchAdvertisement` request after `updateAdvertisement` and a `patchProviderProfile` request after `updateProviderProfile` in their flows: `Content-Type: application/merge-patch+json`, `If-Match` from the stored version variable, a one-field body, a test asserting 200 and that an untouched field is still present, storing the new version for the following steps. Step metadata header (`step`, `label` <= 24 characters, `kind: optional`) as the README defines. If step 5 ran, change the `updateMySettings` body to one field. In `10-rest-api-flow.spec.js`, inside the advertisement test and the provider-profile story, add one PATCH call that changes one field and asserts the others are kept.
- **Verification:** `bash docs/api-collections/api-collection-tests.sh` against the running app (after `/deploy-and-run`), no findings; `bash scripts/playwright.sh 10-rest-api-flow --ux` green.
- **Sources:** none beyond the project.

### Step 8 -- module documentation

- **Depends on:** 3, 4, (5)
- **Files:** `.claude/rules/marketplace-rest-api.md`, `marketplace-rest-api/README.md`
- **Executor:** haiku
- **Input:** both files.
- **Change:**
  - `.claude/rules/marketplace-rest-api.md`: insert, directly before the line starting with `` - `org.ost.restapi.config` — `ApiSecurityConfig` ``, this bullet:
    ```
    - `org.ost.restapi.api.patch` — `MergePatchSupport`, a Spring component that applies a JSON Merge
      Patch (RFC 7396) to a controller's own write request record built from the resource's current
      state, rejects a member that is not a field of that record, and validates the result with the
      record's own constraints (`PatchValidationException`, mapped to 400 by `ApiExceptionHandler`).
      Used by `PATCH /api/advertisements/{id}`, `PATCH /api/provider-profiles/{id}` and
      `PATCH /api/users/me/settings`; the merged record then goes through the same save path as `PUT`.
    ```
    (Drop the settings path from the last sentence if step 5 did not run.)
  - `marketplace-rest-api/README.md`: replace the two lines
    ```
      `PATCH /api/users/me/settings` lets a caller change their own saved pagination-size preferences
      the same way.
    ```
    with
    ```
      Advertisements and provider profiles also take `PATCH` with a JSON Merge Patch body (RFC 7396):
      only the fields sent change, `null` clears an optional field, and the result goes through the
      same validation, permission check and history entry as `PUT`, which stays a full replace.
      `PATCH /api/users/me/settings` changes a caller's own saved pagination-size preferences the same
      way.
    ```
- **Verification:** `git diff` shows only these edits; `bash .claude/nav/scripts/check-rule-path-globs.sh` is not needed (no `paths:` change).
- **Sources:** none.

### Step 9 -- decision record and task file

- **Depends on:** 3-8 done and green
- **Files:** via `/record-decision marketplace-app — REST partial updates use JSON Merge Patch over the PUT write record` (it writes the ADR and regenerates `.claude/nav/adr-index.md`); `backlog/tasks/improvement-186-rest-hypermedia-media-subresource-and-patch-support.md` (mark the PATCH part done, Operational notes); then the task lifecycle in `.claude/rules.md` when the person asks to close.
- **Executor:** sonnet
- **Input:** `plan-human.md` decisions as answered; `notes.md` "Variants and comparison".
- **Change:** the ADR states the decision (merge patch, own helper, no dependency, 400 for an invalid result, both media types, unknown members rejected), the rejected alternatives with one reason each (JsonNullable: 0.x dependency + second record per resource; JSON Patch: no Jackson 3 library, poor fit for flat resources; documentation only: leaves the silent wipe), and `**Also affects:** marketplace-rest-api, marketplace-orchestrator`.
- **Verification:** the command's own checks pass; the index lists the new ADR.
- **Sources:** all rows in `sources.md`.

### Verification of the whole

1. `bash scripts/build-and-test.sh --unit --integration --sandbox` green (wrapped by `scripts/activity-monitor.sh`), including `ArchitectureRulesTest` (operationIds) and `OperationCoverageTest`.
2. `/deploy-and-run`, then `GET /v3/api-docs` shows `patchAdvertisement` and `patchProviderProfile` with `application/merge-patch+json` and `application/json` request bodies; in Swagger UI, "Try it out" on `patchAdvertisement` with `{"cityTaxonId": null}` and the current ETag returns 200 and the categories are unchanged (record whether Swagger UI sent the merge-patch media type correctly).
3. `bash docs/api-collections/api-collection-tests.sh` no findings.
4. `bash scripts/playwright.sh 10-rest-api-flow --ux` green.
5. Manual curl: `PUT /api/advertisements/{id}` without `cityTaxonId` still clears it (PUT unchanged).
6. `git diff --stat` touches only the files named in the steps.

### Final verification at the end of all work (requested 2026-10-28)

Run once, after the last change of this task, in this order:
1. **Clean the dev database** (`bash scripts/deploy-and-run.sh --reset-only-db`, wrapped by `scripts/activity-monitor.sh`): the development bucket was deleted on 2026-10-28 while the database still held 28 uploaded-file attachment rows (in 8 advertisements) pointing at files that no longer exist, so their gallery URLs answer 404.
2. **Live gallery check over REST** on the cleaned stack: issue a test API key, upload a small image to an advertisement (`POST /api/advertisements/{id}/media`), confirm the returned URL answers 200, list the gallery, delete the item, then remove the test user and key.
3. **All other possible checks**: the full chain of "Verification of the whole" above, then `bash scripts/ci.sh` for unit, integration, end-to-end and Sonar, and read the real per-stage numbers.

## Operational notes
- token_cost_review: n/a
- token_cost_research: 210596
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: plan a REST PATCH design in depth before approving it
- flows_chosen: /deep-research
- flows_matched: yes
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
