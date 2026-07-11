# Issue Backlog — prioritized execution order

Index of all open issues in `issues/`, grouped by execution wave. Each issue file carries the
same assignment in its `**When:**` line — if they ever disagree, the issue file wins and this
index must be updated.

Waves: **Week 0** = cheap independent quick wins; **Wave 1** = prerequisites for public
shareability; **Wave 2** = quality hardening before public traffic; **Wave 3** = with the
corresponding domain work; **Deferred** = trigger-based, do not touch until the trigger fires.

---

## Week 0 — quick wins (~1 day total)

✅ Done (2026-07-04): toast position (improvement-012, commit 0f02b91d), header email overflow
(improvement-009, commit 0f02b91d), unused vaadin-core dependency (improvement-016, commit
0f02b91d), virtual threads (commit 0f02b91d), DelegatingPasswordEncoder (commit 0f02b91d),
owasp-sanitizer bump 20240325.1→20260313.1 (commit a9ed6d7e) — all verified with a full
reactor build, `deploy-dev.sh`, and a green 46/46 e2e run each time. Resolved issues moved to
`completed/issues/`.

Also fixed this session, not originally tracked as Week-0 items: a Quill false-dirty-state bug
and 3-layer description length validation (commit b7d64cc2 — closes
`completed/issues/issue-description-length-tag-spam.md`, unblocks improvement-006; see
`marketplace-app/DECISIONS.md` ADR-021 update and ADR-024).

✅ Done (2026-07-10): buildx + BuildKit cache mounts (Part 1/3 of process-improvements, commit
8f29a12f) — installed `docker buildx`, added `--progress=plain` streaming to `deploy.sh`, and
`--mount=type=cache` for `/root/.m2` and `/root/.vaadin` in the Dockerfile build stage. Cuts
the Maven/Vaadin build step from ~182s to ~145s on a `--no-cache` rebuild. A `node_modules`
cache mount was tried and reverted — `vaadin-maven-plugin` rm-rf's and recreates that directory
every build, incompatible with a fixed cache mountpoint.

**Week 0 is now complete.**

## Wave 1 — prerequisites for public shareability

✅ Done (2026-07-04): improvement-005 — plain-text card excerpt (Jsoup `.text()` instead of raw
`innerHTML`) + sanitizer allowlist merge (`<pre>` added, `mailto:`/extra tags kept as accepted
divergence). Moved to `completed/issues/`. Updated an outdated Playwright assertion
(`e2e/_flows/advertisement.flow.js`, card step) that expected rich HTML tags in the card —
full e2e 46/46 green.

✅ Also done: improvement-017 step 1 — upload size cap lowered `500 MB → 50 MB`
(`AttachmentUploadButton.java:9`), sized for realistic ad photos/short demo videos. Issue file
stays open (not moved) — step 2 (real async pipeline) remains deferred, see Deferred section.
Full e2e 46/46 green.

✅ Done (2026-07-07): improvement-020 — security baseline. Deny-by-default
(`anyRequest().denyAll()`) was implemented, deployed, and broke the whole app (0/46 e2e — root
Vaadin route never rendered under a real browser hit, only `curl` was tested first). Reverted
to `anyRequest().permitAll()`; see `marketplace-app/DECISIONS.md` ADR-025 for why deny-by-default
doesn't apply to this app's single-route Vaadin SPA model, and the resulting process rule for
future REST controllers. Rate limiting (Caffeine, `AuthService.login()` /
`UserService.register()`) implemented, then corrected to count only real failures — not
successes — after it broke bulk e2e signups from a shared IP (see ADR-026). Moved to
`completed/issues/`. Full e2e suite 47/47 green (47, not 46 — new `rateLimitUser` test added to
spec 02).

✅ Done (2026-07-11): improvement-007 — `TaxonPort.findByIds()` bulk lookup (kills the N+1 in
`DefaultTaxonPort.resolveDtos()`/`buildDtoIndex()`) + `AttachmentSnapshotService
.captureAndGetId()`. Bundled with improvement-004 — `PaginationSqlBuilder` extracted to
query-lib, `deleted_by` added to `taxon` (edited directly into `001-taxon.xml` since the DB
isn't in production yet, not a new migration). Both moved to `completed/issues/`. Editing an
already-applied changeset required a full `deploy.sh --reset` (Liquibase checksum mismatch
otherwise). Full e2e suite 47/47 green.

✅ Done (2026-07-11): improvement-022 — registration rate limiter shared-bucket risk (found
2026-07-10 by external audit). `server.forward-headers-strategy: framework` added to
`application-prod.yml` so `request.getRemoteAddr()` resolves the real client IP behind Render's
proxy instead of Render's own edge address. See `marketplace-app/DECISIONS.md` ADR-027 (also
records why a coarser global backstop limiter was considered and dropped — registration
failures have no natural per-target key to count against, unlike login). Moved to
`completed/issues/`. Full e2e suite 47/47 green. Note: whether Render actually forwards
`X-Forwarded-For` isn't verifiable from this dev environment — worth confirming once actually
deployed.

**Wave 1 is now fully complete.**

## Wave 2 — quality hardening before public traffic

| Order | Issue | What | Note |
|---|---|---|---|
| 1 | [improvement-015](issues/improvement-015-optimistic-locking.md) | Optimistic locking (@Version on all entities) | silent last-write-wins otherwise |
| 2 | [improvement-013](issues/improvement-013-raw-field-names-in-activity-diff.md) | Localized field labels in Activity diffs | infrastructure (labelFor) already exists |

✅ Done (2026-07-04): tag-spam validator + 3-layer Jsoup-based length validation
(`issue-description-length-tag-spam` → moved to `completed/issues/`), alongside a fix for a
Quill false-dirty-state bug (edit form showed Save/Discard as active on open for rich-text
descriptions — not separately tracked as an issue, fixed directly; see
`marketplace-app/DECISIONS.md` ADR-021 update). Full e2e 46/46 green.

✅ Done (2026-07-11): improvement-018 — `SettingsPaginationService` cross-session settings
bleed (real multi-user bug: user X's page size change was silently applied to every other
logged-in user's live grid) + UI-reference leak risk (cleanup relied solely on `@PreDestroy`).
Fixed by adding `userId` ownership to `BindingEntry` and a `bar.addDetachListener(...)` safety
net; see `marketplace-app/DECISIONS.md` ADR-028. Moved to `completed/issues/`. Extended the
existing page-size Playwright test with a second-session bleed check instead of a new spec
file. Full e2e suite 47/47 green.

**Still open, no longer blocked:**

| Issue | What | Note |
|---|---|---|
| [improvement-006](issues/improvement-006-quill-description-counter-and-db-limit.md) | Quill char counter + DB VARCHAR(2000) migration | its blocker is resolved; can be picked up independently now |
| 6 | [improvement-011](issues/improvement-011-unguarded-port-injection-in-ui-components.md) | Port-injection guards decision (Option A/C) | must precede creation of any new starter |

Plus from `process-improvements.md`: ArchUnit module + minimal CI (before the codebase grows).

## Wave 3 — with the corresponding domain work

| Issue | What | Pairs with |
|---|---|---|
| [improvement-002](issues/improvement-002-snapshot-schema-versioning.md) | Snapshot schema versioning | before the first new snapshot-bearing domain |
| [improvement-019](issues/improvement-019-findtimeline-correlated-subqueries.md) | findTimeline window-function rewrite | any audit-starter touch |

Plus: Testcontainers test layer is a hard gate before any payment code.

## Deferred — trigger-based (do not touch until the trigger fires)

| Issue | Trigger |
|---|---|
| [improvement-003](issues/improvement-003-deferred-performance.md) (items A-K) | per-item triggers inside the file |
| [improvement-021](issues/improvement-021-attachment-concurrency-and-batching.md) | concurrent gallery editing in practice; item A joins any attachment schema touch |
| [improvement-017](issues/improvement-017-sync-s3-upload-in-request-thread.md) (step 2) | bundled with the thumbnail-pipeline refactor |
| [improvement-008](issues/improvement-008-deleted-category-strikethrough.md), [improvement-010](issues/improvement-010-advertisements-view-refresh-error-notification.md), [improvement-014](issues/improvement-014-uuid-filenames-in-media-diff.md) | cosmetic — batch into any nearby UI-touching PR |
| [goal-001](issues/goal-001-activity-field-visibility-by-role.md) | user feedback |

---

## Maintenance rules

- New issue → add its `**When:**` line AND a row here, in the same change.
- Issue resolved → move the file to `completed/issues/` (per rules.md) AND remove its row
  here, in the same change.
