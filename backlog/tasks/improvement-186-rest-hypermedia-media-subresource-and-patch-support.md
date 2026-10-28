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

- Add `PATCH /api/advertisements/{id}` for the fields where "send it, or leave it alone" is the
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
