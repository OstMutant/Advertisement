# improvement-236: List order without a sort, and the free bump on edit — research and fix, with REST and MCP in step with the UI

**Type:** bug and decision — an API list that can repeat or skip items between pages, and a default order that rewards editing; research first, then fix
**Module:** `advertisement-spring-boot-starter` (`AdvertisementRepository`, its `SORT_FIELDS`), `query-lib` (`OrderByBuilder`, `SortField`), `marketplace-rest-api` (`AdvertisementApiController`, `SortQueryParser`, and the other list controllers), `marketplace-mcp` (the list tools), `marketplace-app` (`AdvertisementQueryConfig`, `AdvertisementSortMeta`)
**Priority:** 🟡 top — a real defect in the public API that is independent of every other task; the UI is not affected today, so it is cheap to keep the UI as the reference
**When:** independent, no blockers; found by the research for [improvement-235](improvement-235-promotion-mechanism.md), which it also unblocks

## Current state

Verified against the code on 2026-11-05:

- `GET /api/advertisements` takes an optional `sort`. `SortQueryParser.parse` returns `Sort.unsorted()` when it is missing or blank (`marketplace-rest-api/.../paging/SortQueryParser.java`).
- `AdvertisementRepository.findByFilter` builds `ORDER BY` with `OrderByBuilder.build(pageable.getSort(), SORT_FIELDS)`. The `SORT_FIELDS` carry no default direction (`SortField.of(...)` without one), so an unsorted call produces a query with no `ORDER BY` at all, followed by `LIMIT` and `OFFSET`. PostgreSQL then returns rows in no guaranteed order, and consecutive pages can repeat or skip items.
- The Vaadin catalog never hits this: `AdvertisementQueryConfig` always sends `updatedAt` descending, then `createdAt` descending.
- `updatedAt` is set by `@LastModifiedDate` on `Advertisement`, so every edit moves the advertisement to the top of the UI's default order, for free.
- The same shape (sort fields without a default direction) exists in the provider profile, user and taxon starters; whether their REST lists share the problem is not checked yet.
- The MCP tools are a thin wrapper over the REST operations, so an MCP client inherits whatever the REST list does.
- The REST and MCP surface is meant to mirror the UI's filter, sort and paging; the decision on record for it is the one that gave the REST list endpoints UI parity (the entry in `marketplace-app/DECISIONS.md` that `.claude/nav/adr-index.md` lists for `GET /api/*` filter/sort/pagination, with the taxon part later reversed).

## Why change

A client that pages through the REST list without a `sort` can silently miss advertisements or read some twice, and an AI agent using the MCP list is such a client. Paging with a stable order is a basic contract of any paged API.

The free bump on edit is a product question, not a bug: it lets anyone keep an advertisement on top by editing it, which also removes any value a paid "bump" could have. It is a separate decision, but it sits in the same default order, so the two are best settled together.

## Expected benefit

Every list that is paged has a stable, documented default order, the same one whichever door is used (UI, REST, MCP); no repeated or skipped items; the question of whether editing may bump an advertisement is answered on purpose.

## Approach

Research first, in this order:
1. **Find every list with the problem.** Check each REST list (advertisements, provider profiles, users, taxons, feedback, history, moderation queue, API keys) and each MCP list tool for a default order, and whether the UI sends one. Reproduce the repeat or skip with a small data set (the integration tests have a real Postgres).
2. **Decide the default order and where it lives.** Options: a default direction on the `SORT_FIELDS` of each repository, a default `Sort` in the service that all three doors share, or a final tie-breaker on `id` added by `OrderByBuilder` whenever the caller's sort does not already end in a unique key. The last one also protects a caller-chosen sort on a non-unique column such as `title`. The UI's own default (`updatedAt` descending, then `createdAt`) is the reference the other doors should match.
3. **Decide the bump on edit.** Options: keep it (editing refreshes an advertisement, as on many classifieds), sort the default by `createdAt` instead, or add a separate "bumped at" that edits do not touch. Whatever is chosen is a product decision with a recorded reason.
4. **Fix and prove parity.** The REST operation text, the OpenAPI description, the MCP tool descriptions and the UI agree on the default; an integration test per list pages through a set larger than one page and checks that nothing repeats or is missing; the Postman collections and flow runner are updated where they assume an order.

## Research result (2026-11-05, step 1 done from the code)

- `OrderByBuilder.build` already supports a default order: a `SortField` created with a direction is appended after the caller's sort, and its tiebreakers after it. Only the audit timeline and the moderation queue use it; their lists are safe.
- Four paged lists have no default and no unique final key, so an unsorted REST or MCP call has no `ORDER BY`: advertisements, provider profiles, users, taxons (`SORT_FIELDS` in each repository). The taxon REST list is not paged and orders by `id`, so it is not affected.
- A caller-chosen sort on a non-unique column (`title`, `description`, `kind`, `name`, `email`, `role`) has no tiebreaker in those four lists either, so ties page unstably. Only `createdAt` and `updatedAt` end in `id`.
- The feedback list per profile orders by `created_at DESC` alone; ties are possible, with no `id`.
- The UI always sends `updatedAt desc, createdAt desc` (advertisements, providers, users) or `createdAt desc` (timeline, moderation), so it never meets the problem.
- The MCP list tools forward `sort` unchanged and say nothing about the default, so their behaviour is the REST behaviour.

## Plan (decided with the user, 2026-11-05)

Repositories are not touched. The default order lives in the REST controllers, as the audit and moderation controllers already do; MCP wraps REST and inherits it.

1. `SortQueryParser.parse(sort, allowed, defaultSort)`: uses `defaultSort` when no `sort` is given and always ends the order with `id` descending (unless `id` is already present), so non-unique sorts such as `title` page stably.
2. The advertisement, provider profile and user list controllers declare `DEFAULT_SORT` (`updatedAt` descending, then `createdAt` descending), the same order the Vaadin views send, and call the new overload.
3. The REST operation descriptions and the MCP `sort` parameter descriptions of those three lists state the default order.
4. Tests: `SortQueryParserTest` covers the new overload; the three controller tests expect the default sort; a repository-level paging test proves no repeat or skip across pages.
5. Bump on edit: kept as it is (the default stays `updatedAt`); revisit when visibility is sold.

Not covered here: the taxon list needs no change (its REST list is not paged and always orders by `id`); the per-profile feedback list keeps its tie key in the repository, which this task does not touch.

## Related

- [improvement-235](improvement-235-promotion-mechanism.md) — the research that found this; a paid placement design should not start before the default order is settled.
- `marketplace-app/DECISIONS.md` and `.claude/nav/adr-index.md` — the decisions on REST list endpoints, paging and parity with the UI (read them before changing the contract).
- `.claude/rules/marketplace-rest-api.md` and `.claude/rules/marketplace-mcp.md`.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: no
- context_loading_matched: n/a
- flows_situation: running the maximum local CI and syncing its artifacts back
- flows_chosen: scripts/ci.sh (the ci command) then scripts/ci/run.sh --sync-artifacts
- flows_matched: yes
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none

### Script/command runs
- scripts/build-and-test.sh --unit --integration --sandbox | duration_s=583 | mode=background | result=pass
- scripts/build-and-test.sh --no-unit --integration --sandbox | duration_s=410 | mode=background | result=fail (one wrong test assumption, fixed)
- scripts/ci.sh (maximum run) | duration_s=1344 | mode=background | result=pass
- scripts/ci.sh (maximum run, after the new scenario tests) | duration_s=n/a | mode=background | result=pass
- scripts/ci/run.sh --sync-artifacts | duration_s=191 | mode=foreground | result=pass
