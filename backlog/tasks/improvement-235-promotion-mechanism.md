# improvement-235: Promotion placement — a flexible mechanism that lifts advertisements and profiles out of the crowd

**Type:** feature — one mechanism that marks provider profiles (advertisements later) as promoted and presents them to searchers first; the reason for promoting is pluggable (today a rating rule, later a purchase); research done, design decided and built as one list with a badge, a filter and a sort
**Module:** `platform-commons` (a port and the DTOs), a new starter or an existing one that owns the promotion state, `marketplace-orchestrator` (composition with ratings and display), `advertisement-spring-boot-starter` and `provider-profile-spring-boot-starter` (what can be promoted), `feedback-spring-boot-starter` (`feedback_aggregate` as the first criterion), `marketplace-app` (the promoted display in the catalog and the Providers view), `marketplace-rest-api` and `marketplace-mcp` (a promoted list in step with the UI)
**Priority:** 🔵 top at the user's request — a larger feature that needs a design decision; the long-term plan is to earn from it, so the structure matters more than the first rule
**When:** built; closed once the user's review has no open questions

## Current state

Verified against the code on 2026-11-05:

- There is no notion of a promoted advertisement or profile anywhere. The catalog is one list filtered by `AdvertisementFilterDto`, sorted by the caller's sort, paged by page number; the Providers catalog is the same shape; the REST list returns `X-Total-Count` and an RFC 8288 `Link` header and the MCP tools wrap it.
- Ratings exist: `feedback_aggregate` keeps `avgRating` and `reviewCount` for each owning entity (type and id, `FeedbackAggregateDto`), recomputed on every feedback write by the feedback starter, and `FeedbackAccessService` in the orchestrator reads it. So a rule like "five stars from at least five reviews" can be computed today.
- The project has a pattern for a feature that belongs to an entity without touching the entity's table: a separate starter tied by entity type and id (the taxon assignment and the feedback aggregate work that way), composed in `marketplace-orchestrator`.

## Why change

The owner wants to lift the best entries out of the mass and show them to searchers first, and to be able to charge for it later. The way to make that last is one mechanism with the reason for promoting kept separate from the promoting itself, so that a new reason (a rating today, a purchase or a moderator's choice later) does not mean rebuilding the display, the lists or the APIs. Done as a one-off sort tweak, every new reason would touch every list and every door (UI, REST, MCP) again.

## Expected benefit

Searchers see the best or the sponsored entries first without the list getting harder to read or less trustworthy; the platform gets a place to hang later reasons for promoting; a new reason for promoting is a small addition, not a redesign.

## Research result (plan for people)

> Superseded for everything about a strip, a nightly job, a `/promoted` endpoint and an MCP tool: the "Rework: one list instead of a strip" section below is the current design. Kept as the history of the decision.

The research below proposed a strip of cards above the list. That design was replaced by the rework
further down (a badge on the card, a "promoted" filter and sort inside the one list), and the execution
steps 8, 10, 11 and 12 were carried out for the strip first and then superseded by R4 to R6; the research
text is kept as the record of how the decision was reached.

Purpose: the result for the person who decides: clear, short and easy to follow.

### Goal

One mechanism that lifts some provider profiles and advertisements out of the general mass and shows
them to searchers first, for a reason that can change (today a rating rule, later a purchase or a
moderator's pick), without rebuilding the display, the lists or the APIs each time a reason is added,
and shown so that it helps searchers instead of annoying them.

### The problem this solves

Today every catalog is one list in the caller's own order. There is nowhere to put "the best" or, later,
"the paid" entries. Done as a sort tweak, every new reason would touch every list and every door (UI,
REST, MCP) again, and a paid entry would push an earned one down without telling the reader.

### Conflicts to decide first (raised before the plan, not buried in it)

1. **The example rule "5 stars from 5 reviews" works against the goal.** An average of exactly 5.0
   is the band readers trust least (purchase likelihood peaks at 4.0-4.7 and falls toward 5.0, Spiegel
   Research Center), and one 4-star review would drop the master out. Programs that award an earned
   badge use about 4.8 plus a minimum number of reviews (Airbnb Superhost 4.8 and 10 stays; Thumbtack
   Top Pro 4.8 and 5 reviews; Etsy Star Seller 4.8). See decision 1.
2. **A profile owner can review their own profile today.** No code path checks it (the review form,
   the REST call and the orchestrator only check that the profile exists). With a 5-review threshold
   the owner's own review is a fifth of the qualification. See decision 2.
3. **Best practice removes a promoted entry from the normal results; the brief says the lists stay
   untouched.** Algolia's guide and eBay show each entry once. Removing it would change page contents
   and the meaning of the total count. See decision 3.
4. **A recorded decision assumed ratings never show on cards.** The decision that kept the feedback
   starter self-contained (see `.claude/nav/adr-index.md`, the feedback/`EntityRef` entry) was written
   when ratings appeared only on a profile's own page. The promotion strip shows the rating on a card for
   the first time. That decision already allows it (read through the feedback port); the design record
   should add a note to it, not contradict it.

### The answer to the six questions

#### 1. The shape of the mechanism

A **promotion placement** is one row: *which entry* (entity type and id), *why* (a reason: `TOP_RATED`
now; `SPONSORED` or `EDITORIAL` later), *from when* and *until when* (empty "until" = until its writer
removes it). One table covers advertisements and profiles, the same way reviews and contacts already
work: its own small starter, keyed by entity type and id, no column on the profile or advertisement
tables.

A reason is not code inside the display. It is **a writer** that owns its own rows:

| Mechanism | How it works | SQL cost | Consistency | A purchase later | Verdict |
|---|---|---|---|---|---|
| m1 rule on read | Every catalog load asks the feedback starter "who qualifies now" | One extra cross-module query per page load | Badge flickers with every review | Needs a stored grant anyway, so a second mechanism appears | Rejected |
| m2 table rebuilt by a job | A job recomputes the whole promoted set | Cheap reads, one job | Stable between runs | A rebuild would wipe purchased rows unless split by reason, which is m3 | Rejected as a special case of m3 |
| **m3 table of grants, one writer per reason** | The rating rule is a nightly writer that keeps its own `TOP_RATED` rows in step; a purchase later inserts `SPONSORED` rows with an end date; a moderator `EDITORIAL` rows | Cheap reads (one small table), one small job | Stable between runs, like Superhost and Top Pro | Adds rows, nothing else changes | **Proposed** |

#### 2. Which entries qualify

- **Proposed first rule:** a provider profile is "Top rated" when its average is at least **4.8** from
  at least **5** visible reviews. Hidden and confirmed-violation reviews are already left out of the
  average, so no extra filter is needed. The thresholds are settings, not code.
- **Checked once a night**, like the existing clean-up jobs, so status changes at most once a day and a
  single review does not make a card appear and vanish. When the rating drops, the entry leaves at the
  next run. When moderation hides a fake review, its effect is gone by the next morning.
- **Ties and too many entries:** the strip shows at most 4, picked at random among the qualifying
  entries that match the searcher's filter, a new pick on each load, so every qualifying entry gets an
  equal share over time (OLX rotates its TOP set the same way).
- **Small market:** with a few hundred entries per city, fewer than 4 may qualify; the strip then shows
  what exists, and disappears when nothing matches.
- **Against gaming:** one review per user and profile and 10 reviews a day per user already exist; the
  missing piece is the self-review guard (decision 2). Later: count only reviews older than the 48-hour
  edit window, and a minimum account age for reviewers.
- **Advertisements** have no reviews of their own (reviews attach only to provider profiles). An
  advertisement can be promoted by inheriting its owner's profile status, with an honest label
  ("From a top-rated master"), or only for a purchase or a moderator's pick. See decision 4.
- **Realistic next reasons:** moderator pick (`EDITORIAL`), verified provider, new provider boost
  (time-limited), purchase (`SPONSORED`). A purchase adds only an end date and the word "Реклама".

#### 3. How to show it without annoying searchers

| Display | Paging and total count | Caller's filter and sort | REST and MCP | Trust and UX | Size | Risk |
|---|---|---|---|---|---|---|
| (a) badge on the normal card only | untouched | untouched | one more field | honest for earned reasons, misleading for paid | smallest | gives no exposure |
| (b) rank boost in the sort | page 1 fills with promoted | breaks "newest first" and the caller's choice | changes the list | displaced normal results cost readers (Joo et al.) | needs a column or a join across modules | high |
| **(c) separate strip above the list** | **untouched** | **matches the filter, ignores the sort** | **separate endpoint** | **Algolia's documented pattern; labelled apart** | **small** | **low** |
| (d) promoted slots inside the list | page sizes and totals stop matching | partly | per-item flag in the list | repeats the same few entries in a small market | medium | medium |
| (e) block inside the first page | page 1 shifts, totals shift | partly | changes the list | page 1 can become all promoted | medium | medium |
| (f) a "Top rated" page or filter chip | untouched | untouched | a filter value | no exposure unless clicked | small | low, good as a later "see all" |

**Proposed: (c).** A strip of at most 4 cards above the list, only on the first page, no automatic
rotation (moving content is ignored and looks like an ad, NN/g; it would also need a pause control,
WCAG 2.2.2), the same card the list uses with a small label, and a "Why these?" link that states the
rule in one sentence (the EU ranking-transparency rule asks for the main parameters in plain words; the
badge study found unexplained badges distrusted). The entry also stays in its normal place in the list
(decision 3).

Desktop, Providers tab, filter "Plumbing, Kyiv":

```
 Providers                                         [Filters v]  [Sort: newest v]
 +-- Top rated . Plumbing, Kyiv -------------------------------- (i) Why these? --+
 | +----------------+ +----------------+ +----------------+ +----------------+  |
 | | * Top rated    | | * Top rated    | | * Top rated    | | * Top rated    |  |
 | | Ivan P. MASTER | | Dmytro K.      | | Aqua Shop SHOP | | Oleh S. MASTER |  |
 | | 4.9 * (12)     | | 4.8 * (7)      | | 5.0 * (5)      | | 4.8 * (21)     |  |
 | | Plumbing, Kyiv | | Plumbing, Kyiv | | Plumbing, Kyiv | | Plumbing, Kyiv |  |
 | +----------------+ +----------------+ +----------------+ +----------------+  |
 +-------------------------------------------------------------------------------+
 All providers . 128
 [card] [card] [card] [card]
 [card] [card] [card] [card]
                              < 1  2  3 ... 11 >
```

"Why these?" opens one line: *"Providers with an average of at least 4.8 from at least 5 reviews.
Updated daily. Shown in random order."*

Mobile: the same strip as one row that scrolls sideways with the next card half visible (NN/g), no dots.

```
 +-- Top rated ---------------- (i) --+
 | +--------------+ +-------         |
 | | * Top rated  | | * Top           |
 | | Ivan P.      | | Dmytr           |
 | | 4.9 * (12)   | | 4.8 *           |
 | +--------------+ +-------         |
 +------------------------------------+
```

States: nothing matches the filter, so no strip at all (no empty box); one match, so one card with the
same label; page 2 and later, so no strip. The heading is a real heading and each card a link, so a
screen reader announces "Top rated, list of 3".

Alternatives, for comparison:

```
 (a) badge only                    (d) interleaved                  (b) rank boost
 [card][card *Top][card]           [card][card][card]               [*card][*card][*card]
 [card][card][card]                [* Top rated card]               [*card][card][card]
                                   [card][card][card]               ("newest first" is no longer true)
```

Later, a paid reason gets **its own** strip labelled "Реклама" / "Advertising", never mixed into the
"Top rated" one, so the label always tells the true reason.

#### 4. The other doors

- **REST:** a separate `GET /api/provider-profiles/promoted` with the same filter parameters as the
  list, returning a plain array of at most 4 items, each with its reason and rating. The list endpoint,
  its total count and its paging links do not change. Public, like the list.
- **MCP:** one more tool, `listPromotedProviderProfiles`, wrapping that endpoint, so an agent sees the
  same strip a person sees.
- **Sitemap, link previews, search engines:** no effect. The sitemap reads the plain list, a link
  preview reads one entry, and the strip shows the same content to everyone (not cloaking).

#### 5. Fit with the architecture

- A new `promotion-spring-boot-starter` owns one table, `promotion_placement`, with a unique key on
  (entity type, entity id, reason). It knows nothing about profiles, advertisements or reviews.
- `platform-commons` gets the port (`PromotionPlacementPort`: "list active placements for a type",
  "replace the rows of one reason"), one DTO and the reason enum.
- The feedback port gets one query ("which entities have at least this average from at least this many
  reviews"); the provider profile port gets one ("which of these ids match this filter").
- The orchestrator holds two small services (each within the two-port limit): the nightly rating rule,
  and the read that joins promoted rows, the filter, the rating and the display data.
- No caching in the first version (two small queries per first-page load). Rows written by the rating
  rule are not audited, because they can be recomputed from the reviews, which are audited; a moderator
  or purchase writer will audit its rows.
- **Where a purchase plugs in:** the payment module calls the same port with reason `SPONSORED` and an
  end date. Added then: one enum value, one label in the two language files, one method to write a
  single grant, and the separate "Реклама" strip. Unchanged: the table, the read service, the REST
  endpoint shape, the MCP tool and the catalog lists.

#### 6. Smallest first version and sequence

First version: the rating rule on **provider profiles** and the strip in the **Providers** tab, with
REST, MCP, tests and documentation. Left for later: advertisements (decision 4), moderator picks,
purchases, a "see all top rated" filter, counting only reviews older than 48 hours.

### The means needed

- One new starter (table, repository, service, port implementation), one port, one DTO and one enum in
  `platform-commons`, one query each on the feedback and provider-profile ports.
- Two orchestrator services and one nightly job with its settings.
- One Vaadin strip component, styles and the language keys; one REST endpoint; one MCP tool; the
  Postman collections.
- Integration tests (repository, orchestrator, REST), one Playwright scenario, the design record and the
  documentation updates.

### What the result will look like

A searcher opening Providers sees up to 4 "Top rated" cards that match the filter, above the usual
list, with a one-line explanation of why. Nothing else in the list changes. An agent calling REST or
MCP gets the same four. When a purchase comes, it fills a separately labelled strip through the same
table.

### Cost

Effort: about 4 to 6 working days of a developer, done here as 14 steps.

| Executor | Steps | Estimated tokens |
|---|---|---|
| haiku | 2, 6, 13b | about 110 thousand |
| sonnet | 1, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13a, 14 | about 2.45 million (range 2.0 to 2.8) |
| opus | none (the design is decided by your answers below) | 0 |

Total about 2.1 to 2.9 million tokens, most of it in the starter, the UI strip and the tests. The
estimate is mine and not measured.

### Risks

- **Too few qualifying providers** at launch: the strip is often absent. Low harm (no empty box);
  lowering the threshold is a setting.
- **Fake reviews** inflate a profile into the strip until moderation acts. Self-review guard
  (decision 2) and nightly runs limit it.
- **Duplicates on page 1** (decision 3): the same provider may appear in the strip and in the list.
- **Reading the strip as advertising:** a label "Top rated" plus the "Why these?" line reduce it; the
  Danish authority's test showed a clear label nearly triples awareness of why a result is placed.
- **Order with improvement-236:** the strip has its own order (random) and does not depend on the list
  order, so building it need not wait; the task file says it waits. See decision 8.

### Where the project's practice differs from best practice

- **Deduplication** (conflict 3): best practice shows each entry once; the project's rule that lists stay
  untouched keeps duplicates. Cost of following best practice: a filter on page 1 and a total that no
  longer equals what the reader counts.
- **Earned status checked on a schedule** (Airbnb, Thumbtack, Etsy): the project recomputes the rating
  live on every write. The proposal keeps the live rating and adds a nightly check only for promoting;
  no change to the project's practice.
- **Ranking explanation:** best practice (EU ranking rules, badge studies) explains why an entry is
  placed; the project has no such text anywhere yet. The proposal adds one line to the strip.
- **Earlier sketch in the owner's notes** put a promotion column on the advertisement table with a sort
  priority; the project's practice (own starter by entity type and id) and the brief's decided generic
  mechanism replace it. Recorded only for traceability.


## Decisions taken (user, 2026-11-05)

1. **Threshold:** a provider profile is "Top rated" at an average of at least 4.8 from at least 5 visible reviews; both are settings.
2. **Self-review:** a profile owner is blocked from reviewing their own profile when the review is created; done as its own task, [improvement-238](../completed/tasks/improvement-238-block-self-review-of-provider-profile.md), not as a step of this one.
3. **Duplicates:** a promoted profile also stays in its normal place in the list, so the list, its total count and its paging do not change.
4. **Strip size:** at most 4 cards, picked at random among the qualifying profiles that match the caller's filter, first page only, no automatic rotation. A later idea: a "show all top rated" filter chip.
5. **Label:** "Top rated" / "Високий рейтинг" with a "Why these?" / "Чому ці?" line stating the rule; a paid reason later gets its own strip labelled "Реклама" / "Advertising".
6. **Frequency:** the rating rule reacts immediately to every review change (decided again on 2026-11-05, replacing the nightly run) and recounts once at start-up.
7. **Advertisements:** not decided now; the mechanism is generic over entity type, so an advertisement reason (for example inherited from the owner's profile) can be added later without changing the table.
8. **Order of work:** build after improvement-236 (now done), as the user chose; the research found the strip does not depend on the list order, so this is a sequencing choice, not a technical need.

The execution plan below is the research's plan for the recommended first version. Its step 9 (self-review guard) is replaced by improvement-238; everything else applies as written, with the decisions above as its precondition.

## Flexibility review: strategies and nuances (open — how to extract them is not decided)

Checked against the project's accepted templates on 2026-11-05. The plan below is **not yet changed**
for any of these; each item is a candidate to fold into the steps once the user decides how to
extract it.

### Strategies the project already uses (reuse, do not invent new ones)

| Need | Accepted template in the project | Where it applies here |
|---|---|---|
| A feature tied to any entity without touching its table | Generic `entity_type` + `entity_id`, no FK, own starter, reached through a port in `platform-commons` | The placement table and `PromotionPlacementPort` (already followed) |
| Replace a whole set in one call | `TaxonPort.replaceAssignments(entityType, entityId, taxonIds)` — "replaces all in one transaction" | `PromotionPlacementPort.replaceForReason` has the same shape |
| A closed set of values shared by modules | A plain enum in `platform-commons` (`EntityType`, `AdKind`, `FeedbackModerationStatus`) | `PromotionReason` |
| A label for an enum value in the UI | `I18nKey.forXxx(enum)` in `marketplace-app` (for example `forAdKind`) | `I18nKey.forPromotionReason`, so a new reason that has no label fails to compile if the switch has no default |
| A scheduled job | A `*Properties` record with `@DefaultValue`, a service with one method, and one `SchedulingConfigurer` bean in the module's autoconfiguration (`userCleanupScheduler`) | The nightly rating rule |
| Settings of one module | The properties record lives in that module's own `config` package (`TaxonProperties`, `McpProperties`); only a record shared by several modules goes to `platform-commons` (`CleanupProperties`) | `PromotionProperties` belongs in the orchestrator, not in `platform-commons` |
| Per-entity-type behaviour | A `switch` or `EnumMap` inside one class (`EntityExistenceService`, `AuditTimelineListRenderer`); there is no registry of strategy beans anywhere in the project | See nuance 3 |

### Decisions (resolved 2026-11-05 from the code and outside sources)

| # | Decision | Evidence |
|---|---|---|
| 1 | The strip is read **per reason**: `findActive(entityType, reason)` and `findForStrip(reason, filter, locale)`; REST and MCP take an optional `reason`, default `TOP_RATED`. A new reason adds its own strip and leaves `TOP_RATED` untouched; no tie-break on enum order. | Paid and earned placements are separate and labelled on marketplaces (eBay marks ads with a badge and gives each slot to one listing); the EU Digital Services Act, art. 26, asks that each ad is identifiable and says who paid. One strip per reason satisfies both without branching. |
| 2 | Two flat settings records, no nested one: `PromotionProperties` (`app.promotion`: `stripSize`) and `PromotionTopRatedProperties` (`app.promotion.top-rated`: `minAverage`, `minReviews`, `cronExpression`, `timezone`). | Every properties record in the project is flat, one per prefix (`CleanupProperties`, `TaxonProperties`, `ApiWriteLimitProperties`); none is nested. |
| 3 | A `PromotionRule` interface (one bean per reason) and a single `PromotionRefreshService` entry point that any process calls explicitly; the reaction is immediate, there is no nightly job. | Replaces the earlier "no interface" verdict: once any process must be able to trigger a reaction, the rule side has to be pluggable. The project has no registry of strategy beans, but Spring injects a `List` of beans plainly and the calls stay explicit, which fits "explicit over implicit". A purchase is not a rule (the payment module writes its own reason). |
| 4 | One port for read and write now; split when the payment module adds its grant method. | The user-port split rule: split only when real consumers cluster into separate concerns, never by method count; outside guidance also advises splitting only when there is a reason. |
| 5 | `PromotionReason` stays a closed enum in `platform-commons`; the label comes from `I18nKey.forPromotionReason`. | `EntityType`, `AdKind` and `FeedbackModerationStatus` are closed enums with a label mapping in the app; a missing label fails to compile. |
| 6 | One row per entity type, id and reason (the unique index); a purchase is an upsert of that row. Whether extending a purchase stacks or resets the period is decided in [improvement-237](improvement-237-purchase-reason-and-payment-module.md). | eBay allows a listing in one general campaign at a time; the sources do not settle stacking versus resetting. |
| 7 | Strip services stay per entity type (provider profile now, advertisement later), sharing helpers and no base class. | The advertisement and provider-profile enrichment and save services are built this way with a shared `CategoryAndCitySplit`. |

## Rework: one list instead of a strip (decided with the user, 2026-11-06)

The strip above the list is replaced by one concept inside the list itself, so a purchase can be added
later without a new interface:

- **A badge on the card** for every promoted profile. Everyone sees why in one word (the earned reason
  reads "Top rated"; the paid reason will read "Реклама" / "Advertising", the word the advertising law
  names; a softer synonym can be considered later, see
  [improvement-237](improvement-237-purchase-reason-and-payment-module.md)). Privileged users additionally
  see the details (the reason, until when).
- **One filter "Promoted only"** over every reason, and **one sort "Promoted"**, on by default: promoted
  profiles first, the paid reason above the earned one, then the user's own order. Changing the sort
  merges them into the ordinary list. The list total is unchanged unless the filter is on.
- The strip, the `/promoted` endpoint and its MCP tool are removed; the list endpoints and the list
  tool carry the filter, the sort and the badge fields instead (UI, REST and MCP stay in step).

Steps (each its own approved plan before it is executed):

- **R1 `platform-commons`, value types only (compiles on its own; done 2026-11-06, the full unit suite and `PromotionReasonTest` pass).** `PromotionReason` gets `rank` (lower
  first; today `TOP_RATED`); new record `ProviderProfileScope(Set<Long> onlyIds, List<Set<Long>>
  pinnedGroups, boolean pinnedAfterRest)` with `ALL`; `ProviderProfileFilterDto` gains `Boolean promoted`;
  `ProviderProfileDto` gains the display fields `promoted`, `promotionReason`, `promotionValidUntil`.
  The port changes follow with their implementers: the profile port in R2, the promotion port in R3; the
  methods the strip used (`findMatchingAmong`, the per-reason read) are removed in R6, together with the
  last consumer of the strip.
- **R2 `provider-profile-spring-boot-starter` and its port. (done 2026-11-06).** `ProviderProfilePort.getFiltered`/`count` gain a
  scope parameter (the present signatures stay as default methods using `ALL`); the repository orders by the pinned groups before the
  caller's sort (`CASE WHEN pp.id = ANY(...)`); the service combines `onlyIds` with the taxon filter's
  ids by intersection (the logic of the removed `findMatchingAmong` moves here) for both the list and
  the count; the SQL filter builder ignores the new filter field.
- **R3 `promotion-spring-boot-starter` and its port. (done 2026-11-06).** `PromotionPlacementPort.findActive(entityType)` returns
  the active rows of every reason.
- **R4 `marketplace-orchestrator` (additions only). (done 2026-11-06).** A `PromotionCatalogService` (the promotion port
  only): `scopeFor(filter, sort)` builds the `ProviderProfileScope` (no query and `ALL` unless the filter
  asks for promoted only or the sort carries `promoted`; the pinned groups are the active placements
  grouped by `PromotionReason.rank`, `pinnedAfterRest` when the direction is ascending, `onlyIds` all
  promoted ids for the filter, an empty set when the promotion starter is absent), `withoutPromoted(sort)`
  strips the `promoted` order, `promotionsOf(ids)` gives the best-ranked placement of each id for badges.
  `ProviderProfileReadService.getFiltered`/`count` use it, so every door gets the same behaviour;
  `ProviderProfileDisplayEnrichmentService.enrichWithPromotion(profiles, withDetails)` sets `promoted`,
  `promotionReason` and, for privileged callers only, `promotionValidUntil`.
- **R5 `marketplace-app`. (done 2026-11-06).** `ProviderProfileFilterMeta.PROMOTED` (a `Boolean`, unchecked means no filter) and
  `ProviderProfileSortMeta.PROMOTED` (property `ProviderProfileDto.Fields.promoted`); one row in
  `ProviderProfileQueryBlock` (the existing one-field-plus-sort-icon helper) with a `Checkbox` "Promoted
  only"; the default sort in `ProviderProfileQueryConfig` starts with `promoted` descending; `ProvidersView`
  calls `enrichWithPromotion(profiles, access.isPrivileged())` next to the other enrichers; the card shows a
  badge (reason label for everyone, a tooltip with the reason and the end date for privileged users);
  removed: the strip component, its factory bean, `promotion-strip.css` and its import, the strip code in
  `ProvidersView`, the strip i18n keys and the two strip key mappings; new keys for the sort, the filter,
  the end-date tooltip; badge styles join `provider-profile-card.css`; the Playwright strip scenario is
  replaced by a list scenario (default order, badge, "Promoted only", the sort toggled off, details only
  for an administrator).
- **R6 `marketplace-rest-api` and `marketplace-mcp` together, then the removals (done 2026-11-06)** (one step, because the
  tool list of the MCP server must equal the operations of the live OpenAPI at every build). REST: `promoted`
  joins the sortable fields and leads the default sort; list items and the single-profile read carry the badge
  fields (the end date only for privileged callers); `/promoted` and its response record are removed; the
  OpenAPI text names the filter, the sort and the new default order. MCP: `listProviderProfiles` takes
  `promoted` and documents the `promoted` sort and the new default order; `listPromotedProviderProfiles` goes
  (also from the hard-coded operation list) and so do the requests added for it in both collections.
  Removed afterwards, since nothing uses them: `PromotedProviderProfileService`, `PromotedProviderProfile`,
  `PromotionProperties` and their tests, the port methods `ProviderProfilePort.findMatchingAmong` and
  `PromotionPlacementPort.findActive(entityType, reason)` with their implementations and tests.
- **R7 merged into R6.**
- **R8 tests.** Repository (pinned order, only ids), service (intersection), catalog service, the list
  scenarios of level 3 (default order, filter, a fifth review, a reported review, a deleted profile),
  the Playwright scenario (badge, filter, default order, details for an administrator) in place of the
  strip scenario, then the full unit, integration, collections and e2e runs.
- **R9 documentation (done 2026-11-06).** The design record's strip sentence, the orchestrator and promotion rules, the
  starter README, the regenerated architecture map and index.

## Execution plan

> Superseded: the steps below are the first plan (strip, nightly job, `/promoted`, MCP tool). The rework section's R-steps are what was built. Kept as history, not as a specification.

Purpose: the result for the AI that executes it, with the detail each step's executor model needs
(exact content for a weaker model, exact files and behavior for a stronger one).

Scope: the recommended first version only: mechanism m3 (a table of placements, one writer per
reason), the `TOP_RATED` rating rule on provider profiles, the promotion strip in the Providers tab,
REST and MCP parity, tests and documentation. Advertisements, moderator picks and purchases are out of
scope.

**Precondition.** The person has answered the decisions in `plan-human.md`. The values below are the
proposed answers (threshold 4.8 and 5, nightly, strip of 4 on the first page only, duplicates kept,
self-review guard included). If an answer differs, change only the named setting or skip step 9; no
other step changes.

Every step obeys the project rules: `.claude/rules.md`, root `CLAUDE.md` and the `.claude/rules/<module>.md`
of the module touched (read an existing file of that module first so its rule file loads); pure SQL via
`JdbcClient`; `lombok.NonNull` on public parameters; Lombok first; comments one line or none, never a
ticket or ADR number; all user-visible text through `I18nKey`; every Liquibase column and table with
`remarks=`. Before each file edit the main thread presents the plan and gets approval (Approval Rule).

### Steps

#### 1. Design record

- **Depends on:** the person's answers.
- **Files:** `platform-commons/DECISIONS.md` (new ADR, via `/record-decision`), annotation on the
  existing feedback/`EntityRef` ADR in the same file, `.claude/nav/adr-index.md` (regenerated).
- **Executor:** sonnet.
- **Input:** `plan-human.md` sections 1-6 and the answers; the existing ADR found in
  `.claude/nav/adr-index.md` as "feedback-spring-boot-starter reuses EntityRef and stays
  self-contained" (extract it with `node .claude/nav/scripts/md-to-decisions-json.js --extract platform-commons <id>`).
- **Change:** run `/record-decision platform-commons — Promotion placement is its own starter keyed by
  entity type, id and reason; each reason has one writer` with: context (the goal, the three mechanisms
  m1/m2/m3 and why m3), decision (table `promotion_placement`, `PromotionPlacementPort`, the rating rule
  as a nightly writer in the orchestrator, the strip outside the paged list, separate REST endpoint and
  MCP tool, duplicates kept), rejected alternatives (rule on read; job-rebuilt table; rank boost;
  interleaved slots; column on the domain table), consequences (a purchase adds a reason value, a
  label, a single-grant port method and its own labelled strip). `**Also affects:**` the new starter,
  `marketplace-orchestrator`, `marketplace-app`, `marketplace-rest-api`, `marketplace-mcp`,
  `feedback-spring-boot-starter`, `provider-profile-spring-boot-starter`. Annotate the feedback ADR:
  "the promotion strip is the first card-level reader of the aggregate, through `FeedbackPort` as this
  decision allows". Regenerate the ADR index (`bash .claude/nav/scripts/generate-adr-index.sh`).
- **Verification:** `/record-decision`'s own checks pass; the index has the new row; no ADR number or
  ticket number appears in any other file.
- **Sources:** https://www.algolia.com/doc/guides/solutions/ecommerce/sponsored-products ,
  https://www.airbnb.com/help/article/829 , https://business.ucr.edu/news/2023/11/28/online-consumers-shy-away
- **Estimate:** about 80 thousand tokens.

#### 2. Port, DTO and reason enum in platform-commons

- **Depends on:** 1.
- **Files (new):**
  `platform-commons/src/main/java/org/ost/platform/promotion/model/PromotionReason.java`,
  `platform-commons/src/main/java/org/ost/platform/promotion/dto/PromotionPlacementDto.java`,
  `platform-commons/src/main/java/org/ost/platform/promotion/spi/PromotionPlacementPort.java`.
- **Executor:** haiku.
- **Input:** none (new files).
- **Exact content:**

`PromotionReason.java`
```java
package org.ost.platform.promotion.model;

/** Why an entry is lifted out of the catalog; each value has exactly one writer that owns its rows. */
public enum PromotionReason {
    TOP_RATED
}
```

`PromotionPlacementDto.java`
```java
package org.ost.platform.promotion.dto;

import java.time.Instant;
import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.model.PromotionReason;

/** One promotion placement: which entry, why, and until when ({@code validUntil} null means until its writer removes it). */
public record PromotionPlacementDto(
        @NonNull EntityType entityType,
        @NonNull Long entityId,
        @NonNull PromotionReason reason,
        @NonNull Instant validFrom,
        Instant validUntil
) {
}
```

`PromotionPlacementPort.java`
```java
package org.ost.platform.promotion.spi;

import java.util.List;
import java.util.Set;
import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.dto.PromotionPlacementDto;
import org.ost.platform.promotion.model.PromotionReason;

/** Promotion placements of any owning entity, generic over entity type and id; implementation lives in promotion-spring-boot-starter. */
public interface PromotionPlacementPort {

    /** Placements of this entity type and reason that are active now. */
    List<PromotionPlacementDto> findActive(@NonNull EntityType entityType, @NonNull PromotionReason reason);

    /** Makes the rows of this reason for this entity type exactly {@code entityIds}; an empty set removes them all. */
    void replaceForReason(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Set<Long> entityIds);
}
```
- **Verification:** `bash scripts/build-and-test.sh --no-unit --no-integration` compiles (run through
  `scripts/activity-monitor.sh` per `.claude/rules.md` "Scripts").
- **Sources:** project precedent `platform-commons/src/main/java/org/ost/platform/feedback/spi/FeedbackPort.java`.
- **Estimate:** about 30 thousand tokens.

#### 3. Feedback port: the qualifying query (done 2026-11-05)

- **Depends on:** 2 (compiles in the same reactor; no code dependency).
- **Files:** `platform-commons/.../feedback/spi/FeedbackPort.java`,
  `feedback-spring-boot-starter/.../repository/FeedbackRepository.java`,
  `feedback-spring-boot-starter/.../services/FeedbackService.java`,
  `feedback-spring-boot-starter/.../spi/FeedbackPortImpl.java`,
  `marketplace-orchestrator/.../services/FeedbackAccessService.java`.
- **Executor:** sonnet.
- **Input:** the files above; `.claude/rules/feedback-spring-boot-starter.md`.
- **Change:**
  - Port: `Set<Long> findEntityIdsWithRatingAtLeast(@NonNull EntityType entityType, double minAverage, int minReviews);`
    with a one-line Javadoc ("entities whose visible-review average and count reach both thresholds").
  - Repository: one text-block query on `feedback_aggregate` only:
    `SELECT entity_id FROM feedback_aggregate WHERE entity_type = :entityType AND avg_rating >= :minAverage AND review_count >= :minReviews`.
    The aggregate already counts only `moderation_status = 'NEW'` reviews; do not join other tables.
  - Service and port implementation: pure delegation, in the "Feedback" section of the service next to `getAggregate`. The average is compared as stored (`NUMERIC(2,1)`, one decimal, bound as `BigDecimal`), the same number the UI shows, so a true 4.75 qualifies as 4.8.
  - No `FeedbackAccessService` wrapper: the rating rule reads `FeedbackPort` through its own `ComponentFactory`, so a second entry point would be unused.
- **Verification:** done: `FeedbackRepositoryTest` (threshold inclusive on both, stored one-decimal average, hidden review excluded, other entity type excluded) and `FeedbackAccessServiceTest` (absent starter gives an empty set) pass.
- **Sources:** https://spiegel.medill.northwestern.edu/how-online-reviews-influence-sales/ ,
  https://pro-center.thumbtack.com/?p=21959
- **Estimate:** about 120 thousand tokens.

#### 4. Provider profile port: match a filter among given ids (done 2026-11-05)

- **Depends on:** 2.
- **Files:** `platform-commons/.../providerprofile/spi/ProviderProfilePort.java`,
  `provider-profile-spring-boot-starter/.../services/ProviderProfileService.java`,
  its port implementation (no new repository method: `findByFilter` with `allowedIds` and an unpaged `Pageable` serves it),
  `marketplace-orchestrator/.../services/ProviderProfileReadService.java`.
- **Executor:** sonnet.
- **Input:** the files above; `.claude/rules/provider-profile-spring-boot-starter.md`.
- **Change:**
  - Port: `List<ProviderProfileDto> findMatchingAmong(@NonNull ProviderProfileFilterDto filter, @NonNull Set<Long> ids);`
    one-line Javadoc: "profiles among {@code ids} that match the filter, unpaged, by id".
  - Service: validate the filter like `getFiltered` (`@Valid`); resolve the taxon filter the same way
    (`resolveTaxonFilter`); if it resolves to an empty set return an empty list (existing pattern);
    otherwise intersect it with `ids` (or use `ids` alone when there is no taxon filter) and call the
    existing `findByFilter` path with that set as `allowedIds`, sorted by `id`, without a page limit.
    Reuse `buildIdClause`; do not add a second id-clause builder. Callers pass a non-empty `ids`; no
    empty guard on `ids`.
  - `ProviderProfileReadService`: wrapper, `List.of()` when the starter is absent.
- **Verification:** done: `ProviderProfileServiceTest` (12 pass) covers no taxon filter, the intersection with the taxon filter, and an empty match; step 13a adds the repository-level case that an id outside the set never appears.
- **Sources:** project precedent `AdvertisementService.getFiltered` (allowedIds from `TaxonPort`).
- **Estimate:** about 120 thousand tokens.

#### 5. The new starter `promotion-spring-boot-starter` (done 2026-11-05)

- **Depends on:** 2.
- **Files (new):** `promotion-spring-boot-starter/pom.xml`;
  `src/main/java/org/ost/promotion/config/PromotionAutoConfiguration.java`;
  `src/main/java/org/ost/promotion/repository/PromotionPlacementRepository.java`;
  `src/main/java/org/ost/promotion/services/PromotionPlacementService.java`;
  `src/main/java/org/ost/promotion/spi/PromotionPlacementPortImpl.java`;
  `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`;
  `src/main/resources/db/promotion-changelog/promotion-changelog-master.xml`;
  `src/main/resources/db/promotion-changelog/changes/01-promotion-schema.xml`.
- **Executor:** sonnet.
- **Input:** `contact-spring-boot-starter/` as the template (pom with `architecture.boundedContext`,
  the enforcer `bannedDependencies` rule against sibling starters, autoconfiguration with Liquibase and
  the `ComponentFactory<...Port>` bean, master changelog with its header comment);
  `.claude/rules/contact-spring-boot-starter.md`.
- **Change:**
  - Schema, table `promotion_placement` (remarks: "A promotion placement of any owning entity: which
    entry, why and until when; one writer owns each reason's rows; no column on the promoted
    entity's own table"):
    `id BIGSERIAL` PK; `entity_type VARCHAR(30) NOT NULL` (EntityType, no FK, resolved through
    `PromotionPlacementPort`); `entity_id BIGINT NOT NULL` (no FK); `reason VARCHAR(30) NOT NULL`
    (PromotionReason; each value has one writer); `valid_from TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()`;
    `valid_until TIMESTAMP WITH TIME ZONE` (null = until the writer removes the row; a purchase sets
    it); `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()`. Unique index
    `idx_promotion_placement_entity_reason (entity_type, entity_id, reason)` and index
    `idx_promotion_placement_type_reason (entity_type, reason)` for the strip read. Every column has
    `remarks=`; the table remark names the design record by its title, not its number.
  - Repository (`@Repository`, `@RequiredArgsConstructor`, `@SuppressWarnings("java:S1192")`,
    `JdbcClient`, `private static final RowMapper<PromotionPlacementDto>`), no entity class and no
    `CrudRepository` (there is no trivial save/find):
    `findActive(entityType, reason)`: `... WHERE entity_type = :entityType AND reason = :reason AND valid_from <= NOW() AND (valid_until IS NULL OR valid_until > NOW())`.
    `deleteForReasonExcept(entityType, reason, Long[] ids)`: `DELETE ... WHERE entity_type = :t AND reason = :r AND NOT (entity_id = ANY(:ids))`.
    `insertMissing(entityType, reason, Long[] ids)`: `INSERT INTO promotion_placement (entity_type, entity_id, reason) SELECT :t, unnest(:ids), :r ON CONFLICT (entity_type, entity_id, reason) DO NOTHING`.
    Bind ids as `Long[]` (same reason as `AdvertisementRepository.buildIdClause`).
  - Service: `findActive` delegates; `replaceForReason` runs delete then insert in one
    `@Transactional` method. No guard for an empty set: `NOT (id = ANY('{}'))` deletes every row of the reason and `unnest('{}')` inserts none.
  - Port implementation: pure delegation.
  - Autoconfiguration: Liquibase bean for this starter's master changelog (same shape as contact),
    `ComponentFactory<PromotionPlacementPort>` bean, component scan of `org.ost.promotion`, and
    `@EnableJdbcRepositories(basePackages = "org.ost.promotion.repository")` like every starter (the test
    allow-list always loads the Spring Data JDBC auto-configuration, which needs it).
- **Verification:** done together with step 6: the reactor compiles and `PromotionPlacementRepositoryTest` (7 tests: insert, idempotent replace, removal, empty set, other reasons and entity types untouched, expired and not-yet-started rows, row mapping) passes.
- **Sources:** https://www.postgresql.org/docs/current/sql-refreshmaterializedview.html (why not a
  materialized view across starters), project precedent ADR for the contact starter (see
  `.claude/nav/adr-index.md`, "contact-spring-boot-starter module owns contact_info").
- **Estimate:** about 300 thousand tokens.

#### 6. Reactor wiring (done 2026-11-05)

- **Depends on:** 5.
- **Files:** `pom.xml`, `.gitignore`, `scripts/sonar/sonar-project.properties`,
  `marketplace-orchestrator/pom.xml`, `integration-tests/pom.xml`.
- **Executor:** haiku.
- **Input:** the five files.
- **Exact edits:**
  - `pom.xml`, modules: old `        <module>feedback-spring-boot-starter</module>` →
    new `        <module>feedback-spring-boot-starter</module>` + newline + `        <module>promotion-spring-boot-starter</module>`.
  - `pom.xml`, dependencyManagement: after the block
    ```
                <dependency>
                    <groupId>org.ost</groupId>
                    <artifactId>feedback-spring-boot-starter</artifactId>
                    <version>${project.version}</version>
                </dependency>
    ```
    insert the same block with `promotion-spring-boot-starter`.
  - `.gitignore`: after `/feedback-spring-boot-starter/target/` add `/promotion-spring-boot-starter/target/`.
  - `scripts/sonar/sonar-project.properties`: after `  feedback-spring-boot-starter/src/main/java,\`
    add `  promotion-spring-boot-starter/src/main/java,\`; after
    `  feedback-spring-boot-starter/target/classes,\` add `  promotion-spring-boot-starter/target/classes,\`.
  - `marketplace-orchestrator/pom.xml`: after the `feedback-spring-boot-starter` runtime dependency
    block, add the same block with `promotion-spring-boot-starter` and `<scope>runtime</scope>`.
  - `integration-tests/pom.xml`: after the `feedback-spring-boot-starter` dependency block (no scope),
    add the same block with `promotion-spring-boot-starter`.
- **Verification:** `bash scripts/build-and-test.sh --no-unit --no-integration` builds the reactor
  including the new module.
- **Sources:** project files only.
- **Estimate:** about 40 thousand tokens.

#### 7. Orchestrator: the promotion rules and the entry point any process can call (done 2026-11-05)

- **Depends on:** 3, 5, 6.
- **Files:** new `marketplace-orchestrator/.../config/PromotionTopRatedProperties.java`, `.../services/PromotionRule.java`,
  `.../services/TopRatedPromotionRule.java`, `.../services/PromotionRefreshService.java`; edit
  `.../config/OrchestratorAutoConfiguration.java`, `.../services/FeedbackAccessService.java`,
  `.../services/ProviderProfileSaveService.java`; port, starter and test additions of the same step
  (see Change).
- **Executor:** haiku for the port additions and the plain records; sonnet for the rest.
- **Input:** `.claude/rules/marketplace-orchestrator.md`; `FeedbackAccessService` (every write that
  changes the aggregate passes through it).
- **Design (decided with the user, 2026-11-05):** the reaction is immediate, there is no nightly
  job. Two sides, both explicit calls (the project avoids hidden framework magic):
  - *Process side:* any process that changes data a rule depends on calls
    `PromotionRefreshService.entityChanged(entityType, id)` after its own transaction, or
    `entityRemoved(entityType, id)` when the entity is gone. The caller knows nothing about rules.
  - *Rule side:* each reason is one bean implementing `PromotionRule`; the refresh service finds the
    rules of that entity type and applies each. A new reason is one new bean, no change to callers.
- **Change:**
  - Port (`PromotionPlacementPort`) gains `addForReason(entityType, reason, entityId)` and
    `removeForReason(entityType, reason, entityId)`; starter: repository delete-one, add reuses
    `insertMissing` with one id, service and `PortImpl` delegation; tests in
    `PromotionPlacementRepositoryTest`.
  - `interface PromotionRule`: `PromotionReason reason()`, `EntityType entityType()`,
    `boolean qualifies(Long entityId)`, `Set<Long> qualifyingIds()`.
  - `TopRatedPromotionRule` (`PROVIDER_PROFILE`, `TOP_RATED`): `qualifies` reads
    `FeedbackPort.getAggregate` through `ComponentFactory<FeedbackPort>` and compares with
    `PromotionTopRatedProperties` (`minAverage` 4.8, `minReviews` 5); `qualifyingIds` uses
    `FeedbackPort.findEntityIdsWithRatingAtLeast`. One domain port.
  - `PromotionRefreshService` (`List<PromotionRule>`, `ComponentFactory<PromotionPlacementPort>`):
    `entityChanged`: for each rule of the type, `addForReason` or `removeForReason` by
    `qualifies`; each rule is isolated (one failing rule does not stop the others) and the method
    never throws into the calling process, it logs. `entityRemoved`: `removeForReason` for every
    reason; strict, it joins the caller's transaction and fails with it, like the existing contact
    cascade, so a deleted entity never leaves a placement behind. `recountAll`: per rule `replaceForReason(qualifyingIds())`, run once from an
    `@EventListener(ApplicationReadyEvent.class)` so a changed threshold or a missed update is
    healed at start-up. Idempotent.
  - Callers: `FeedbackAccessService` calls `entityChanged` after `save`, `flagFeedback`,
    `approveFeedback`, `confirmFeedbackViolation` and `rejectFeedback` (the entity comes from the
    saved dto or from `findFeedbackById` read before the action; for `reject` before the
    deletion), outside the audit transaction so a promotion failure cannot undo a review;
    `ProviderProfileSaveService.delete` calls `entityRemoved` inside its transaction next to the contact
    cascade; every way a profile disappears (account overlay, catalog overlay, card delete, REST
    delete, the user-delete cascade) passes through that one method, verified in the code.
  - Settings: `PromotionTopRatedProperties`, flat, registered next to `CleanupProperties`; the
    `PromotionProperties(stripSize 4)` record is created in step 8, where it is first used.
- **Verification:** done: the full unit suite and all 931 integration tests pass. The integration contexts that list orchestrator beans one by one (two level-2 tests) use `PromotionTestSupport`; `Level3ScenarioTest` and `McpEndToEndTest` list `PromotionAutoConfiguration`. Unit tests for the refresh service (rule applied, failing rule isolated, no
  throw, removal clears every reason) and for `TopRatedPromotionRule`; `FeedbackAccessServiceTest`
  (call after a write; a failing refresh does not fail the write); step 13a Level 2 test of the
  whole path from a review to a placement.
- **Sources:** https://www.airbnb.com/help/article/829 , https://help.etsy.com/hc/en-us/articles/4403058372503
- **Estimate:** about 250 thousand tokens.

#### 8. Orchestrator: the strip read (done 2026-11-05), then superseded by the rework

- **Depends on:** 4, 5, 6, 7 (properties).
- **Files:** new `marketplace-orchestrator/.../services/PromotedProviderProfile.java` (record), new
  `marketplace-orchestrator/.../services/PromotedProviderProfileService.java`.
- **Executor:** sonnet.
- **Input:** `ProviderProfileReadService`, `ProviderProfileDisplayEnrichmentService`,
  `FeedbackAccessService`, `PromotionProperties`.
- **Change:**
  - Record `PromotedProviderProfile(@NonNull PromotionReason reason, double avgRating, int reviewCount, @NonNull ProviderProfileDto profile)`.
  - `List<PromotedProviderProfile> findForStrip(@NonNull PromotionReason reason, @NonNull ProviderProfileFilterDto filter, @NonNull Locale locale)`:
    active placements for `PROVIDER_PROFILE` and this reason (empty list when the promotion starter is absent); when
    there are none, return an empty list without further calls; `ProviderProfileReadService.findMatchingAmong(filter, ids)`;
    shuffle; keep `stripSize`; enrich with categories/city and actor via
    `ProviderProfileDisplayEnrichmentService`; read each kept profile's aggregate through
    `FeedbackAccessService.getAggregate` (at most `stripSize` calls). The caller's sort is not taken.
    Dependencies: `ComponentFactory<PromotionPlacementPort>` (the only domain port) and orchestrator
    services.
- **Verification:** done: `PromotedProviderProfileServiceTest` (6) and the architecture rules (24) pass; step 13a adds the Level 2 test against a real database: filter respected, at most `stripSize`, empty when nothing qualifies, no profile outside the promoted set.
- **Sources:** https://www.algolia.com/doc/guides/solutions/ecommerce/sponsored-products ,
  https://www.vidhuk.ua/olx/review-2185307 (rotation)
- **Estimate:** about 180 thousand tokens.

#### 9. Self-review guard (moved to improvement-238)

- **Depends on:** none.
- **Files:** `marketplace-orchestrator/.../services/FeedbackWriteService.java`,
  `marketplace-app/.../ui/views/components/FeedbackPanel.java`,
  `marketplace-rest-api/.../api/FeedbackApiController.java` (OpenAPI text only), language files.
- **Executor:** sonnet.
- **Input:** the files above; `SelfActionNotAllowedException` (already mapped by `ApiExceptionHandler`).
- **Change:** in `create`, after loading the profile, throw `SelfActionNotAllowedException("Cannot
  review your own provider profile")` when `profile.getActorId()` equals `actingUserId`. In
  `FeedbackPanel`, do not offer the add form when the signed-in user is `ownerUserId` (the field
  already exists); show nothing instead. Add the response code to the REST operation's
  `@ApiResponse` list with the status `ApiExceptionHandler` already uses.
- **Verification:** step 13a: `FeedbackMatrixScenarioTest` gets the owner case; Playwright: the
  owner sees no review form on their own profile.
- **Sources:** https://wsgr.com/en/insights/ftc-issues-final-rule-banning-fake-and-misleading-consumer-reviews-and-testimonials.html
- **Estimate:** about 100 thousand tokens.

#### 10. Vaadin: the promotion strip in the Providers tab (done 2026-11-05), then superseded by the rework

- **Depends on:** 8.
- **Files:** new `marketplace-app/.../ui/views/main/tabs/providers/PromotedProviderProfileStrip.java`,
  edit `ProvidersView.java`, new `marketplace-app/src/main/frontend/themes/my-app/promotion-strip.css`
  (and its import wherever the theme imports the other view CSS files), `I18nKey.java`,
  `messages_en.properties`, `messages_uk.properties`, the UI bean registration if cards use
  `UiComponentFactory` (`ComponentFactoryConfig`).
- **Executor:** sonnet.
- **Input:** `ProvidersView`, `ProviderProfileCardView` (its kind badge is the model for the label),
  `.claude/rules/marketplace-app.md`, `.claude/rules.md` "View Pattern".
- **Change:**
  - The strip: a section with a heading (the reason label, plus the active filter's category and
    city names when set), a "Why these?" control that opens a Vaadin `Popover` with one line built
    from `PromotionProperties` values, and up to 4 items. Each item reuses `ProviderProfileCardView`
    unchanged inside a wrapper that adds the label "Top rated" and the line "4.9 ★ (12)". No
    automatic rotation, no dots, no arrows. CSS: one row of cards on desktop; on narrow screens a
    single horizontally scrolling row with the next card partly visible (`overflow-x: auto`,
    `scroll-snap-type`). Heading is a real heading element; the item list has `role="list"` and an
    `aria-label` from i18n.
  - `ProvidersView.refresh()`: on the first page only, call `findForStrip(PromotionReason.TOP_RATED, filter, locale)` and render
    the strip; hide it when empty or on any later page. The strip call has its own try/catch inside
    `refresh()` so a failure hides the strip and logs, while the list still renders. `init()` order:
    the strip goes between the query block and the card container. The list, count and
    `PaginationBar` code stays unchanged.
  - i18n keys (en / uk): `promotion.reason.top_rated` = "Top rated" / "Високий рейтинг";
    `promotion.strip.why` = "Why these?" / "Чому ці?"; `promotion.strip.why.top_rated` = "Providers with
    an average of at least {0} from at least {1} reviews. Updated as reviews change. Shown in random order." /
    "Виконавці із середньою оцінкою щонайменше {0} не менше ніж з {1} відгуків. Оновлюється разом із відгуками.
    Порядок випадковий."; `promotion.strip.rating` = "{0} ★ ({1})" in both; `promotion.strip.list`
    = "Top rated providers" / "Виконавці з високим рейтингом". Use the placeholder syntax the existing
    keys use.
- **Verification:** done: the full unit suite and the `04-provider-profile-flow` spec (15 passed) with the strip scenario: absent with four reviews, present after the fifth (exactly 4.8 from 5), a profile with too few reviews stays out, two promoted profiles in the strip, the "Why these?" explanation hidden until opened and closed again, the list unchanged, the strip inside a 375 px screen with its own scrolling row and the next card partly visible, a filter matching nothing hides the strip. The explanation is a native `details`/`summary`: the Vaadin `Popover` web component is not part of this app's frontend build (its Java class only rendered the content inline), and a text-only test check had hidden that. The heading shows only the reason (the filter holds ids, not names). Known and outside this task: the query block already overflows a 375 px screen, a matter for the responsive pass.
- **Sources:** https://www.nngroup.com/articles/auto-forwarding/ , https://www.nngroup.com/articles/mobile-carousels/ ,
  https://www.w3.org/WAI/tutorials/carousels/ , https://searchengineland.com/new-eu-regulations-compel-more-transparency-for-search-rankings-344482 ,
  https://arxiv.org/abs/2204.12552
- **Estimate:** about 350 thousand tokens.

#### 11. REST: `GET /api/provider-profiles/promoted` (done 2026-11-06), then superseded by the rework

- **Depends on:** 8.
- **Files:** `marketplace-rest-api/.../api/ProviderProfileApiController.java`,
  `marketplace-rest-api/.../config/ApiSecurityConfig.java`.
- **Executor:** sonnet.
- **Input:** the `list` method of the controller (filter binding per the REST list ADR in
  `.claude/nav/adr-index.md`, "External REST API list endpoints").
- **Change:** `@GetMapping("/promoted")`, `operationId = "listPromotedProviderProfiles"`, binds
  `@ModelAttribute @Valid ProviderProfileFilterDto` and `locale` exactly like the list, plus an optional
  `reason` parameter (`PromotionReason`, default `TOP_RATED`), returns
  `List<PromotedProviderProfile>` as a plain array, no `Link`, no `X-Total-Count`. Description:
  "Public. Up to 4 provider profiles promoted now, matching the same filter parameters as
  listProviderProfiles, in random order. Each item carries its reason (TOP_RATED: an average of at
  least 4.8 from at least 5 visible reviews, kept up to date as reviews change), its rating and the profile. Not paged and
  not counted in listProviderProfiles' X-Total-Count; the same profile also appears in that list.
  Empty array when none." (numbers from `PromotionProperties`, not hard-coded, if the annotation
  allows; otherwise keep the text and note the settings name). Security: add
  `"/api/provider-profiles/promoted"` explicitly to the public GET matcher list (it is already covered
  by `/api/provider-profiles/**`; the explicit line documents it).
- **Verification:** done: `PromotedProviderProfileScenarioTest` (7 cases: unknown reason, nobody qualifies, four reviews, fifth review with the list and its total unchanged, a filter matching nothing, a reported review drops the profile at once, a deleted profile is removed at once), two controller contract tests, and `OperationCoverageTest`; `ApiSecurityConfig` needed no change because every GET under `/api/provider-profiles/**` is already public.
- **Sources:** https://www.algolia.com/doc/guides/solutions/ecommerce/sponsored-products ,
  https://developers.google.com/search/docs/essentials/spam-policies
- **Estimate:** about 150 thousand tokens.

#### 12. MCP tool and Postman collections (done 2026-11-06), then superseded by the rework

- **Depends on:** 11.
- **Files:** `marketplace-mcp/.../tools/ProviderProfileTools.java`, `marketplace-mcp/.../rest/RestPath.java`
  if paths are listed there, `docs/api-collections/postman/marketplace-rest-api.postman_collection.json`,
  `docs/api-collections/postman/marketplace-mcp.postman_collection.json`, `docs/api-collections/README.md`
  if it lists operations.
- **Executor:** sonnet.
- **Input:** `listProviderProfiles` tool as the pattern; `.claude/rules/marketplace-mcp.md`;
  `docs/api-collections/DECISIONS.md` (MCP flows cover every tool).
- **Change:** tool `listPromotedProviderProfiles` with the list tool's filter parameters minus `page`
  and `sort`, plus `locale` and an optional `reason` (default `TOP_RATED`); description states what the REST description states. Add one REST request
  and one MCP flow step to the collections, asserting a 200 and an array.
- **Verification:** done: `McpEndToEndTest` parity and `ToolNamesTest` (the hard-coded operation list) pass; both collections pass `api-collection-tests.sh` (REST 159 of 161 steps, MCP 154 of 156, the rest conditional). The run also exposed that the Feedback and Moderation flows of both collections had the profile owner write the review, which the self-review guard refuses; the reviewer in those flows is now another user (the administrator in the Moderation flow).
- **Sources:** project ADR "The MCP server is a separate module ... wraps the REST API" (see
  `.claude/nav/adr-index.md`).
- **Estimate:** about 200 thousand tokens.

#### 13a. Tests (done 2026-11-06)

- **Depends on:** 3-12.
- **Files:** `integration-tests/src/main/java/.../support/TestDataCleaner.java` (add
  `promotion_placement`; done), `Level3ScenarioTest` and `McpEndToEndTest` (name `PromotionAutoConfiguration` in
  their autoconfiguration lists once an orchestrator bean needs the `ComponentFactory<PromotionPlacementPort>`;
  the `RepositoryTestAutoConfig` allow-list does not list starters), `level1/promotion/PromotionPlacementRepositoryTest.java` (done), additions to
  `level1/feedback/FeedbackRepositoryTest.java` and the provider-profile repository or service test,
  new `level2/orchestrator/PromotionTopRatedRuleServiceTest.java` and
  `PromotedProviderProfileServiceTest.java`, new `level3/restapi/PromotedProviderProfileScenarioTest.java`,
  `FeedbackMatrixScenarioTest.java` (owner case, if step 9), a Playwright spec under `playwright/e2e/`.
- **Executor:** sonnet.
- **Input:** `.claude/rules/integration-tests.md`, `.claude/rules/playwright.md`, the existing
  feedback tests as the pattern.
- **Change (cases):** replace is idempotent; rows missing from the set are removed; an empty set
  removes all rows of that reason; rows of another reason are untouched and `findActive` returns only the asked reason; a row with `valid_until` in
  the past (inserted by SQL in the test) is not active. Threshold inclusive (4.8 and 5 qualify, 4.7 or
  4 reviews do not); a hidden review drops a profile below the count. Filter by kind, category and city
  among ids. Rule service writes exactly the qualifying ids. Strip service: at most 4, matches the
  filter, empty without placements, only the asked reason's profiles. REST: public, filter applied, array shape, the list endpoint's
  `X-Total-Count` for the same filter is unchanged. Playwright (`--ux`): the strip is absent when
  nothing qualifies; present with label and "Why these?" when a profile qualifies at start-up. If the
  e2e stack cannot make a profile qualify before the start-up run, cover the absent state only and
  record the gap in the task file.
- **Status:** covered by `PromotionPlacementRepositoryTest` (level 1), the Mockito tests of the rule, the refresh service, the strip service and the profile service (including the intersection with the taxon filter), `FeedbackRepositoryTest` (stored average, thresholds, hidden review), `PromotedProviderProfileScenarioTest` (level 3, the whole path from a review or a deletion to the endpoint, with a real database) and the Playwright strip scenario. A separate level-2 test of the rule against the database was not written: the level-3 scenario already runs that path end to end.
- **Verification:** `bash scripts/build-and-test.sh --unit --integration --sandbox`; `bash scripts/playwright.sh e2e --full --ux`
  (both through `scripts/activity-monitor.sh`).
- **Sources:** https://www.evanmiller.org/how-not-to-sort-by-average-rating.html (why a minimum count).
- **Estimate:** about 450 thousand tokens.

#### 13b. Documentation lines

- **Depends on:** 5.
- **Files:** `CLAUDE.md`, `.claude/rules/README.md`.
- **Executor:** haiku.
- **Exact edits:**
  - `CLAUDE.md`, module layout: after the line starting `├── feedback-spring-boot-starter` insert
    `├── promotion-spring-boot-starter        — Promotion placement domain: which entries are lifted out of the catalog and why, generic over EntityRef-owning entities, PromotionPlacementPortImpl (auto-configured starter)`.
  - `CLAUDE.md`, platform-commons package list: after the line starting `- \`feedback.*\`` insert
    ``- `promotion.*` — `promotion.spi` (`PromotionPlacementPort`), `promotion.dto` (`PromotionPlacementDto`), `promotion.model` (`PromotionReason`)``.
  - `CLAUDE.md`, pointer lines: after `→ Feedback domain (rating+text feedback, owned classes): \`.claude/rules/feedback-spring-boot-starter.md\``
    insert a blank line and `→ Promotion placement domain (promoted entries by reason, owned classes): \`.claude/rules/promotion-spring-boot-starter.md\``.
  - `CLAUDE.md`, the "Note:" paragraph listing modules with no hand-authored `DECISIONS.md`: add
    `promotion-spring-boot-starter` to that list and change "seven" to "eight" in both places.
  - `.claude/rules/README.md`: after the `feedback-spring-boot-starter.md` bullet insert
    ``- [`promotion-spring-boot-starter.md`](promotion-spring-boot-starter.md) — `promotion-spring-boot-starter/**` ``.
- **Verification:** `git diff` shows only these lines.
- **Estimate:** about 40 thousand tokens.

#### 14. Module documentation and whole-plan verification

- **Depends on:** all.
- **Files:** new `.claude/rules/promotion-spring-boot-starter.md` (shape of the contact rules file:
  what it owns, schema, key constraints including "no Maven dependency on any sibling starter" and
  "each reason has one writer"), new `promotion-spring-boot-starter/README.md` (per
  `.claude/skills/module-readme-standards/SKILL.md`), `.claude/rules/marketplace-orchestrator.md` (the
  two new services and the scheduler), `.claude/rules/marketplace-app.md` if it lists Providers view
  components, the generated architecture model and pointer `DECISIONS.md` files.
- **Executor:** sonnet.
- **Change:** write the files; run `bash .claude/nav/scripts/check-rule-path-globs.sh` for the new rule
  file; run `bash docs/architecture/scripts/generate-architecture-model.sh`; run
  `/check-architecture-map`; regenerate the ADR index.
- **Verification of the whole:**
  1. `bash scripts/build-and-test.sh --unit --integration --sandbox` green.
  2. `bash scripts/playwright.sh e2e --full --ux` green.
  3. API collections green (step 12).
  4. With the app running: `GET /api/provider-profiles/promoted` without a key returns 200 and an
     array of at most 4; `GET /api/provider-profiles` returns the same `X-Total-Count` as before for
     the same filter; `/sitemap.xml` is unchanged.
  5. The MCP tool list contains `listPromotedProviderProfiles`.
  6. No new file contains a ticket number or an ADR number; every new column has `remarks=`.
- **Estimate:** about 250 thousand tokens.

### Cost summary

| Executor | Steps | Tokens (estimate) |
|---|---|---|
| haiku | 2, 6, 13b | about 110 thousand |
| sonnet | 1, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13a, 14 | about 2.45 million |
| opus | none | 0 |

### Where a purchase plugs in later (not part of this plan)

Add `SPONSORED` to `PromotionReason`; add a port method that writes one grant with `valid_from` and
`valid_until` (the payment module is its only writer, audited with the payer as actor); add the
"Реклама" / "Advertising" label key; render `SPONSORED` items in their own strip above or below the
`TOP_RATED` one, never mixed. The table, `findActive`, the REST endpoint shape and the MCP tool stay
as they are.

## Related

- [improvement-238](../completed/tasks/improvement-238-block-self-review-of-provider-profile.md) — the self-review guard the rating rule relies on.
- [improvement-237](improvement-237-purchase-reason-and-payment-module.md) — the purchase as one more reason for promoting, with the payment module; out of scope here, it plugs into this mechanism later.
- The private product notes (outside version control): the roadmap and the promoted-listings feature spec; read them as input, not as the decision.
- [improvement-236](../completed/tasks/improvement-236-list-order-and-edit-bump-rest-mcp-ui-parity.md) — the default list order and the free bump on edit, found by this task's research; settle it before any placement design.
- `marketplace-app/DECISIONS.md` and `.claude/nav/adr-index.md` hold the decisions on the REST list endpoints, paging and filters that the design must respect.

## Operational notes
- token_cost_review: n/a
- token_cost_research: 230115 (Fable deep-researcher, 70 tool uses, 818 s)
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: ADR-035 (platform-commons)
